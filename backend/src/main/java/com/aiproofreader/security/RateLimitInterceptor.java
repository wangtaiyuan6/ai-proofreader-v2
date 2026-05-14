package com.aiproofreader.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);
    private static final Pattern IP_PATTERN = Pattern.compile("^[\\d.:a-fA-F]+$");
    private static final int MAX_STORE_SIZE = 100_000;

    @Value("${rate-limit.trust-proxy:false}")
    private boolean trustProxy;

    private final ConcurrentHashMap<String, RateLimitEntry> store = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ScheduledExecutorService cleanupExecutor;
    private final AtomicInteger storeSize = new AtomicInteger(0);

    public RateLimitInterceptor() {
        cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "rate-limit-cleanup");
            t.setDaemon(true);
            return t;
        });
        cleanupExecutor.scheduleAtFixedRate(this::cleanup, 5, 5, TimeUnit.MINUTES);
    }

    @PreDestroy
    public void shutdown() {
        log.info("正在关闭 RateLimitInterceptor 清理线程...");
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String path = request.getRequestURI();

        int limit;
        if (path.startsWith("/api/parse")) {
            limit = 20; // 20 per minute for file parsing
        } else if (path.startsWith("/api/proofread")) {
            limit = 10; // 10 per minute for proofreading
        } else {
            return true;
        }

        String clientIp = getClientIP(request);
        log.debug("RateLimitInterceptor: {} {}, IP={}", request.getMethod(), path, clientIp);
        String key = path + ":" + clientIp;
        long now = System.currentTimeMillis();
        long windowMs = 60_000; // 1 minute

        boolean[] isNew = {false};
        RateLimitEntry entry = store.compute(key, (k, existing) -> {
            if (existing == null || now > existing.resetAt) {
                isNew[0] = (existing == null);
                return new RateLimitEntry(1, now + windowMs);
            }
            existing.count++;
            return existing;
        });
        if (isNew[0]) storeSize.incrementAndGet();

        // Evict oldest entries if store grows beyond limit
        if (storeSize.get() > MAX_STORE_SIZE) {
            cleanup();
        }

        if (entry.count > limit) {
            long retryAfter = (entry.resetAt - now) / 1000 + 1;
            sendRateLimitError(response, retryAfter);
            return false;
        }

        return true;
    }

    private void sendRateLimitError(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        objectMapper.writeValue(response.getWriter(), Map.of(
                "error", "请求过于频繁，请稍后再试",
                "retryAfter", retryAfterSeconds
        ));
    }

    private String getClientIP(HttpServletRequest request) {
        if (trustProxy) {
            // Priority 1: X-Real-IP (set by trusted reverse proxies)
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isBlank() && IP_PATTERN.matcher(realIp).matches()) {
                return realIp.trim();
            }

            // Priority 2: X-Forwarded-For (first IP in the list)
            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                String firstIp = forwardedFor.split(",")[0].trim();
                if (IP_PATTERN.matcher(firstIp).matches()) {
                    return firstIp;
                }
            }
        }

        // Fallback: use remote address (not spoofable)
        String remoteAddr = request.getRemoteAddr();
        return remoteAddr != null ? remoteAddr : "127.0.0.1";
    }

    private void cleanup() {
        long now = System.currentTimeMillis();
        int removed = 0;
        var it = store.entrySet().iterator();
        while (it.hasNext()) {
            if (now > it.next().getValue().resetAt) {
                it.remove();
                removed++;
            }
        }
        storeSize.addAndGet(-removed);
    }

    private static class RateLimitEntry {
        volatile int count;
        volatile long resetAt;

        RateLimitEntry(int count, long resetAt) {
            this.count = count;
            this.resetAt = resetAt;
        }
    }
}

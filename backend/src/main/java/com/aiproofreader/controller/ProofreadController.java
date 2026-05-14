package com.aiproofreader.controller;

import com.aiproofreader.service.LlmService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import okhttp3.Call;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@RestController
@RequestMapping("/api/proofread")
public class ProofreadController {

    private static final Logger log = LoggerFactory.getLogger(ProofreadController.class);
    private static final int MAX_TEXT_LENGTH = 10_000;
    private static final long SSE_TIMEOUT_MS = 300_000; // 5 minutes
    private static final int MAX_CONCURRENT_PROOFREAD = 20;
    private static final int MAX_ACTIVE_SSE_CONNECTIONS = 30;

    private final AtomicInteger activeSseConnections = new AtomicInteger(0);

    private final LlmService llmService;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor = new ThreadPoolExecutor(
            2, MAX_CONCURRENT_PROOFREAD, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(50), r -> {
        Thread t = new Thread(r, "proofread-worker");
        t.setDaemon(true);
        return t;
    }, new ThreadPoolExecutor.AbortPolicy());

    public ProofreadController(LlmService llmService, ObjectMapper objectMapper) {
        this.llmService = llmService;
        this.objectMapper = objectMapper;
        log.info("ProofreadController 初始化完成");
    }

    @PreDestroy
    public void shutdown() {
        log.info("正在关闭 ProofreadController 线程池...");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter proofread(@RequestBody Map<String, String> body) {
        log.debug("收到校对请求");

        String text = body.get("text");
        log.debug("文本长度: {}", text != null ? text.length() : "null");

        // Validate input
        if (text == null || text.isBlank()) {
            log.warn("文本为空");
            return createErrorEmitter("请提供需要校对的文本");
        }

        if (text.length() > MAX_TEXT_LENGTH) {
            log.warn("文本过长: {} 字符", text.length());
            return createErrorEmitter("文本过长，最大支持 10000 字符");
        }

        // Check concurrent SSE connection limit
        int currentConnections = activeSseConnections.get();
        if (currentConnections >= MAX_ACTIVE_SSE_CONNECTIONS) {
            log.warn("并发 SSE 连接数已达上限: {}", currentConnections);
            return createErrorEmitter("服务器繁忙，请稍后重试");
        }
        activeSseConnections.incrementAndGet();
        log.debug("当前活跃 SSE 连接数: {}", activeSseConnections.get());

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        AtomicReference<Call> callRef = new AtomicReference<>();

        emitter.onTimeout(() -> {
            log.warn("SSE 连接超时");
            Call call = callRef.get();
            if (call != null) {
                call.cancel();
            }
            sendErrorEvent(emitter, "校对请求超时");
            emitter.complete();
            activeSseConnections.decrementAndGet();
        });

        emitter.onError(e -> {
            log.error("SSE 连接错误: {}", e.getMessage());
            Call call = callRef.get();
            if (call != null) {
                call.cancel();
            }
            try { emitter.complete(); } catch (Exception ignored) {}
            activeSseConnections.decrementAndGet();
        });

        emitter.onCompletion(() -> {
            log.debug("SSE 连接完成");
            activeSseConnections.decrementAndGet();
        });

        // CRITICAL: Run in separate thread to avoid blocking Tomcat thread
        try {
            executor.submit(() -> {
                try {
                    log.debug("开始调用 LlmService.streamProofread");
                    llmService.streamProofread(text, emitter, callRef::set);
                } catch (Exception e) {
                    log.error("校对处理异常: {}", e.getMessage(), e);
                    sendErrorEvent(emitter, "校对过程中发生错误");
                    emitter.complete();
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException e) {
            log.warn("校对线程池已满，拒绝新请求");
            sendErrorEvent(emitter, "服务器繁忙，请稍后重试");
            emitter.complete();
            activeSseConnections.decrementAndGet();
        }

        return emitter;
    }

    private SseEmitter createErrorEmitter(String message) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        sendErrorEvent(emitter, message);
        emitter.complete();
        return emitter;
    }

    private void sendErrorEvent(SseEmitter emitter, String message) {
        try {
            String json = objectMapper.writeValueAsString(Map.of("error", message));
            emitter.send(SseEmitter.event().name("error").data(json));
        } catch (Exception e) {
            log.debug("发送错误事件失败: {}", e.getMessage());
        }
    }

}

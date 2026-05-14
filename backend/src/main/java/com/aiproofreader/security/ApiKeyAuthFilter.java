package com.aiproofreader.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

@Component
@Order(2)
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthFilter.class);

    @Value("${app.api-key:}")
    private String configuredApiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        log.debug("ApiKeyAuthFilter: {} {}", request.getMethod(), path);

        // If no API key is configured, skip auth (dev mode)
        if (configuredApiKey == null || configuredApiKey.isBlank()) {
            log.debug("ApiKeyAuthFilter: 未配置 API Key, 跳过认证");
            chain.doFilter(request, response);
            return;
        }

        if (!path.startsWith("/api/proofread") && !path.startsWith("/api/parse")) {
            chain.doFilter(request, response);
            return;
        }

        String token = extractToken(request);
        if (configuredApiKey.equals(token)) {
            chain.doFilter(request, response);
            return;
        }

        // Unauthorized
        response.setStatus(401);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("WWW-Authenticate", "Bearer realm=\"api\"");
        objectMapper.writeValue(response.getWriter(), Map.of("error", "未授权访问，请提供有效的 API Key"));
    }

    private String extractToken(HttpServletRequest request) {
        // 1. Authorization: Bearer <token>
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }

        // 2. X-API-Key header
        String apiKeyHeader = request.getHeader("X-API-Key");
        if (apiKeyHeader != null && !apiKeyHeader.isBlank()) {
            return apiKeyHeader.trim();
        }

        return null;
    }
}

package com.aiproofreader.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RateLimitInterceptorTest {

    private RateLimitInterceptor interceptor;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private StringWriter responseWriter;

    @BeforeEach
    void setUp() throws Exception {
        interceptor = new RateLimitInterceptor();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        responseWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
    }

    @Test
    void preHandle_proofreadUnderLimit_returnsTrue() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/proofread");
        when(request.getMethod()).thenReturn("POST");

        for (int i = 0; i < 10; i++) {
            assertThat(interceptor.preHandle(request, response, null)).isTrue();
        }
    }

    @Test
    void preHandle_proofreadOverLimit_returnsFalse() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/proofread");
        when(request.getMethod()).thenReturn("POST");

        for (int i = 0; i < 10; i++) {
            interceptor.preHandle(request, response, null);
        }

        // 11th request should be rejected
        assertThat(interceptor.preHandle(request, response, null)).isFalse();
        verify(response).setStatus(429);
    }

    @Test
    void preHandle_parseUnderLimit_returnsTrue() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/parse");
        when(request.getMethod()).thenReturn("POST");

        for (int i = 0; i < 20; i++) {
            assertThat(interceptor.preHandle(request, response, null)).isTrue();
        }
    }

    @Test
    void preHandle_parseOverLimit_returnsFalse() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/parse");
        when(request.getMethod()).thenReturn("POST");

        for (int i = 0; i < 20; i++) {
            interceptor.preHandle(request, response, null);
        }

        assertThat(interceptor.preHandle(request, response, null)).isFalse();
        verify(response).setStatus(429);
    }

    @Test
    void preHandle_nonApiPath_returnsTrue() throws Exception {
        when(request.getRequestURI()).thenReturn("/static/index.html");
        when(request.getMethod()).thenReturn("GET");

        assertThat(interceptor.preHandle(request, response, null)).isTrue();
    }

    @Test
    void preHandle_withRealIp_usesIt_whenTrustProxyEnabled() throws Exception {
        // Enable trustProxy via reflection
        java.lang.reflect.Field field = RateLimitInterceptor.class.getDeclaredField("trustProxy");
        field.setAccessible(true);
        field.set(interceptor, true);

        when(request.getRequestURI()).thenReturn("/api/proofread");
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("X-Real-IP")).thenReturn("10.0.0.1");

        assertThat(interceptor.preHandle(request, response, null)).isTrue();
    }

    @Test
    void preHandle_defaultUsesRemoteAddr() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/proofread");
        when(request.getMethod()).thenReturn("POST");
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");

        assertThat(interceptor.preHandle(request, response, null)).isTrue();
    }

    @Test
    void shutdown_completesWithoutException() {
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> interceptor.shutdown());
    }
}

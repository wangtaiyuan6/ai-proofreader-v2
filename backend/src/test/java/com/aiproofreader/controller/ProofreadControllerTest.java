package com.aiproofreader.controller;

import com.aiproofreader.service.LlmService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ProofreadController 单元测试（纯 Java，不加载 Spring 上下文）。
 */
class ProofreadControllerTest {

    private ProofreadController controller;

    @BeforeEach
    void setUp() {
        // 直接传 null LlmService，仅测试输入校验逻辑
        controller = new ProofreadController(null, new ObjectMapper());
    }

    @Test
    void proofread_emptyText_returnsEmitter() {
        Map<String, String> body = Map.of("text", "");
        SseEmitter emitter = controller.proofread(body);
        assertThat(emitter).isNotNull();
    }

    @Test
    void proofread_blankText_returnsEmitter() {
        Map<String, String> body = Map.of("text", "   ");
        SseEmitter emitter = controller.proofread(body);
        assertThat(emitter).isNotNull();
    }

    @Test
    void proofread_tooLongText_returnsEmitter() {
        String longText = "a".repeat(10_001);
        Map<String, String> body = Map.of("text", longText);
        SseEmitter emitter = controller.proofread(body);
        assertThat(emitter).isNotNull();
    }

    @Test
    void proofread_validText_returnsEmitter() {
        Map<String, String> body = Map.of("text", "测试文本");
        SseEmitter emitter = controller.proofread(body);
        assertThat(emitter).isNotNull();
    }

    @Test
    void shutdown_completesWithoutException() {
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> controller.shutdown());
    }
}

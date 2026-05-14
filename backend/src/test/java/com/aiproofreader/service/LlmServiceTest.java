package com.aiproofreader.service;

import com.aiproofreader.model.Change;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LlmService 测试：验证 HTTP 请求构建、parseChangesJson、shutdown。
 */
class LlmServiceTest {

    private MockWebServer mockServer;
    private LlmService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        mockServer = new MockWebServer();
        mockServer.start();

        service = new LlmService(objectMapper);
        setField(service, "baseUrl", mockServer.url("/v1").toString());
        setField(service, "apiKey", "sk-test-key");
        setField(service, "model", "test-model");

        OkHttpClient mockClient = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
        setField(service, "httpClient", mockClient);
    }

    @AfterEach
    void tearDown() throws Exception {
        mockServer.shutdown();
    }

    // ==================== HTTP 请求验证 ====================

    @Test
    void streamProofread_sendsCorrectRequest() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setBody("data: [DONE]")
                .addHeader("Content-Type", "text/event-stream"));

        SseEmitter emitter = new SseEmitter();
        try { service.streamProofread("测试文本", emitter); } catch (Exception ignored) {}
        Thread.sleep(2000);

        RecordedRequest request = mockServer.takeRequest(5, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getPath()).isEqualTo("/v1/chat/completions");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer sk-test-key");
        assertThat(request.getBody().readUtf8()).contains("测试文本");
    }

    @Test
    void streamProofread_noApiKey_noHttpRequest() throws Exception {
        setField(service, "apiKey", "");
        SseEmitter emitter = new SseEmitter();
        service.streamProofread("测试文本", emitter);
        assertThat(mockServer.getRequestCount()).isEqualTo(0);
    }

    // ==================== parseChangesJson ====================

    @Test
    void parseChangesJson_validJson() throws Exception {
        Method m = LlmService.class.getDeclaredMethod("parseChangesJson", String.class);
        m.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<Change> result = (List<Change>) m.invoke(service,
                "[{\"original\":\"以经\",\"corrected\":\"已经\",\"reason\":\"错别字\"}]");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOriginal()).isEqualTo("以经");
    }

    @Test
    void parseChangesJson_emptyString() throws Exception {
        Method m = LlmService.class.getDeclaredMethod("parseChangesJson", String.class);
        m.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<Change> result = (List<Change>) m.invoke(service, "");
        assertThat(result).isEmpty();
    }

    @Test
    void parseChangesJson_invalidJson_fallsBack() throws Exception {
        Method m = LlmService.class.getDeclaredMethod("parseChangesJson", String.class);
        m.setAccessible(true);

        // 部分 JSON 应该通过 extractCompleteObjects 回退处理
        @SuppressWarnings("unchecked")
        List<Change> result = (List<Change>) m.invoke(service,
                "[{\"original\":\"a\",\"corrected\":\"b\",\"reason\":\"r\"},{\"incomplete\":");
        assertThat(result).hasSize(1);
    }

    // ==================== extractReasoningContent / extractContentFromJson ====================

    @Test
    void extractReasoningContent_withReasoningField() throws Exception {
        Method m = LlmService.class.getDeclaredMethod("extractReasoningContent", String.class);
        m.setAccessible(true);

        String json = "{\"choices\":[{\"delta\":{\"reasoning_content\":\"思考过程\"}}]}";
        String result = (String) m.invoke(service, json);
        assertThat(result).isEqualTo("思考过程");
    }

    @Test
    void extractReasoningContent_withoutReasoningField() throws Exception {
        Method m = LlmService.class.getDeclaredMethod("extractReasoningContent", String.class);
        m.setAccessible(true);

        String json = "{\"choices\":[{\"delta\":{\"content\":\"内容\"}}]}";
        String result = (String) m.invoke(service, json);
        assertThat(result).isNull();
    }

    @Test
    void extractContentFromJson_withContentField() throws Exception {
        Method m = LlmService.class.getDeclaredMethod("extractContentFromJson", String.class);
        m.setAccessible(true);

        String json = "{\"choices\":[{\"delta\":{\"content\":\"校对内容\"}}]}";
        String result = (String) m.invoke(service, json);
        assertThat(result).isEqualTo("校对内容");
    }

    @Test
    void extractContentFromJson_nullContent() throws Exception {
        Method m = LlmService.class.getDeclaredMethod("extractContentFromJson", String.class);
        m.setAccessible(true);

        String json = "{\"choices\":[{\"delta\":{\"content\":null}}]}";
        String result = (String) m.invoke(service, json);
        assertThat(result).isNull();
    }

    // ==================== shutdown() ====================

    @Test
    void shutdown_completesWithoutException() {
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> service.shutdown());
    }

    // ==================== Helpers ====================

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}

package com.aiproofreader.service;

import com.aiproofreader.model.Change;
import com.aiproofreader.model.ParsedResult;
import com.aiproofreader.security.InputSanitizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PreDestroy;
import okhttp3.*;
import okio.BufferedSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Service
public class LlmService {

    private static final Logger log = LoggerFactory.getLogger(LlmService.class);
    private static final double TEMPERATURE = 0.3;

    private static final String SYSTEM_PROMPT = """
            你是一个顶级的文档校对专家。你的任务是校对用户提供的文档。

            【输出格式 — 极其重要】
            你的输出必须包含三个独立的顶层标签，按顺序排列，缺一不可：
            1. <thinking>...</thinking> — 分析过程
            2. <correction>...</correction> — 校对后的完整文档
            3. <changes>...</changes> — 修改记录

            【禁止】<correction> 和 <changes> 必须是独立的顶层标签，绝对不能放在 <thinking> 内部。

            【<thinking> 内容要求】
            禁止输出内心独白、推理过程、自言自语、过渡语（如"好的""我来""首先"）。
            必须严格按以下四个板块输出结构化要点：

            文档体裁分析：[文档类型]（[细分类型]），[发布机构/来源]。
            目标受众：[受众群体]。
            正式程度：[正式程度]，[风格要求简述]。
            校验规则：
            1. [规则名称]：[具体说明，结合文档中的实际例子]。
            2. [规则名称]：[具体说明]。
            3. [规则名称]：[具体说明]。
            （列出 3-5 条，每条必须包含规则名称和具体说明）

            【完整输出示例 — 有修改的情况】
            <thinking>
            文档体裁分析：新闻通讯稿（政务宣传类），由海警机构发布。
            目标受众：社会公众、新闻媒体、相关执法部门。
            正式程度：半正式，兼具官方严肃性与宣传生动性，要求行文规范、用语准确、逻辑清晰。
            校验规则：
            1. 事实准确：核对专有名词、数字、单位。
            2. 语法通顺：检查主谓宾搭配和句子衔接。
            3. 用词规范：避免口语化、歧义表达，确保符合官方宣传口径。
            </thinking>

            <correction>
            校对后的完整文档内容，保持原文格式和段落结构。
            </correction>

            <changes>
            [{"original": "原词句", "corrected": "修改后", "reason": "修改理由"}]
            </changes>

            【完整输出示例 — 无修改的情况】
            <thinking>
            文档体裁分析：...
            目标受众：...
            正式程度：...
            校验规则：...
            </thinking>

            <correction>
            与原文完全一致的完整文档内容。
            </correction>

            <changes>
            []
            </changes>

            【严格要求】
            1. 输出必须是三个独立的顶层标签，顺序：thinking → correction → changes。
            2. <correction> 绝对不能嵌套在 <thinking> 内部。
            3. <thinking> 内只输出四个板块的结构化要点，禁止输出推理过程。
            4. <correction> 内是完整的校对后文档，不要省略任何内容。
            5. <changes> 内是合法 JSON 数组，每条含 original、corrected、reason 字段。
            6. 无错误时 <changes> 必须输出空数组 []，禁止输出示例、占位符或解释性文字。
            7. <changes> 中只能包含真实存在的错误修改，禁止编造、模拟或举例说明。""";

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${app.openai.api-key:}")
    private String apiKey;

    @Value("${app.openai.base-url:https://api.openai.com/v1}")
    private String baseUrl;

    @Value("${app.openai.model:gpt-4o}")
    private String model;

    public LlmService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.MINUTES)
                .writeTimeout(30, TimeUnit.SECONDS)
                .callTimeout(6, TimeUnit.MINUTES)
                .build();
        log.info("LlmService 初始化完成");
    }

    @PreDestroy
    public void shutdown() {
        log.info("正在关闭 LlmService...");
        if (httpClient != null) {
            httpClient.dispatcher().executorService().shutdown();
            httpClient.connectionPool().evictAll();
        }
    }

    /**
     * Stream proofread results via SSE using OkHttp.
     */
    public void streamProofread(String rawText, SseEmitter emitter) {
        streamProofread(rawText, emitter, null);
    }

    /**
     * Stream proofread results via SSE using OkHttp.
     * @param callRef optional callback to receive the OkHttp Call reference for cancellation
     */
    public void streamProofread(String rawText, SseEmitter emitter, Consumer<Call> callRef) {
        log.debug("开始校对处理, 文本长度: {}", rawText != null ? rawText.length() : "null");

        // Validate API key
        if (apiKey == null || apiKey.isBlank()) {
            log.error("未配置 API Key");
            sendError(emitter, "未配置 OpenAI API Key");
            emitter.complete();
            return;
        }

        // Sanitize user input
        String sanitizedText = InputSanitizer.sanitize(rawText);

        // Build request body
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", model);
        requestBody.put("temperature", TEMPERATURE);
        requestBody.put("stream", true);

        ArrayNode messages = requestBody.putArray("messages");
        ObjectNode systemMsg = messages.addObject();
        systemMsg.put("role", "system");
        systemMsg.put("content", SYSTEM_PROMPT);

        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", "请校对以下文档：\n\n<user_document>\n" + sanitizedText + "\n</user_document>");

        String url = baseUrl + "/chat/completions";
        log.info("发起 LLM 请求: {}, 模型: {}", url, model);

        String requestBodyStr;
        try {
            requestBodyStr = objectMapper.writeValueAsString(requestBody);
        } catch (Exception e) {
            sendError(emitter, "请求构建失败");
            emitter.complete();
            return;
        }

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + apiKey)
                .header("Accept", "text/event-stream")
                .post(RequestBody.create(requestBodyStr, MediaType.parse("application/json")))
                .build();

        // Track state
        AtomicReference<String> currentPhase = new AtomicReference<>("thinking");
        StringBuilder accumulatedText = new StringBuilder();
        AtomicBoolean isClosed = new AtomicBoolean(false);
        // Track reasoning_content for R1-class models (DeepSeek etc.)
        StringBuilder reasoningBuffer = new StringBuilder();
        AtomicBoolean hasReasoning = new AtomicBoolean(false);
        // Track sent changes for streaming
        List<Change> sentChanges = new ArrayList<>();
        AtomicBoolean changesStarted = new AtomicBoolean(false);

        // Send start event
        safeSend(emitter, SseEmitter.event().name("start").data("{}"), isClosed);

        Call call = httpClient.newCall(request);
        if (callRef != null) {
            callRef.accept(call);
        }
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.error("LLM 请求失败: {}", e.getMessage(), e);
                if (!isClosed.get()) {
                    sendError(emitter, "校对服务连接失败，请检查网络后重试");
                    safeComplete(emitter, isClosed);
                }
            }

            @Override
            public void onResponse(Call call, Response response) {
                log.debug("收到响应, HTTP Status: {}", response.code());

                if (!response.isSuccessful()) {
                    handleErrorResponse(response, emitter, isClosed);
                    return;
                }

                StreamState state = new StreamState(
                        currentPhase, accumulatedText, reasoningBuffer,
                        hasReasoning, sentChanges, changesStarted);

                try {
                    ResponseBody body = response.body();
                    if (body == null) {
                        log.error("响应体为空");
                        sendError(emitter, "LLM 响应为空");
                        safeComplete(emitter, isClosed);
                        return;
                    }

                    BufferedSource source = body.source();

                    while (!source.exhausted()) {
                        if (isClosed.get()) {
                            call.cancel();
                            break;
                        }

                        String line = source.readUtf8Line();
                        if (line == null) break;
                        if (line.isEmpty() || line.startsWith(":")) continue;

                        if (line.startsWith("data: ")) {
                            String data = line.substring(6).trim();
                            if ("[DONE]".equals(data)) {
                                log.debug("收到 [DONE] 信号");
                                break;
                            }

                            String reasoning = extractReasoningContent(data);
                            String content = extractContentFromJson(data);

                            if (reasoning != null) {
                                processReasoningChunk(reasoning, state, emitter, isClosed);
                                continue;
                            }

                            if (content == null || content.isEmpty()) continue;
                            processContentChunk(content, state, emitter, isClosed);
                        }
                    }

                    if (!isClosed.get()) {
                        handleStreamEnd(state, emitter, isClosed);
                    }

                } catch (Exception e) {
                    log.error("流读取异常: {}", e.getMessage(), e);
                    if (!isClosed.get()) {
                        sendError(emitter, "校对过程中发生异常，请稍后重试");
                        safeComplete(emitter, isClosed);
                    }
                } finally {
                    response.close();
                }
            }
        });
    }

    /**
     * Extract reasoning_content from R1-class model (DeepSeek etc.).
     * Returns null if not present.
     */
    private String extractReasoningContent(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            JsonNode choices = node.get("choices");
            if (choices != null && choices.isArray() && !choices.isEmpty()) {
                JsonNode delta = choices.get(0).get("delta");
                if (delta != null) {
                    JsonNode rcNode = delta.get("reasoning_content");
                    if (rcNode != null && !rcNode.isNull()) {
                        String val = rcNode.asText();
                        if (!val.isEmpty() && !"null".equals(val)) {
                            return val;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("extractReasoningContent 解析异常: {}", e.getMessage(), e);
        }
        return null;
    }

    /**
     * Extract content delta from a single SSE JSON data payload.
     * Checks reasoning_content first (R1 models), then content (standard models).
     */
    private String extractContentFromJson(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            JsonNode choices = node.get("choices");
            if (choices != null && choices.isArray() && !choices.isEmpty()) {
                JsonNode delta = choices.get(0).get("delta");
                if (delta != null) {
                    JsonNode contentNode = delta.get("content");
                    if (contentNode != null && !contentNode.isNull()) {
                        String val = contentNode.asText();
                        if (!val.isEmpty() && !"null".equals(val)) {
                            return val;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("extractContentFromJson 解析异常: {}", e.getMessage(), e);
        }
        return null;
    }

    private void sendSseEvent(SseEmitter emitter, String eventName, Object data, AtomicBoolean isClosed) {
        if (isClosed.get()) return;
        try {
            String json = objectMapper.writeValueAsString(data);
            emitter.send(SseEmitter.event().name(eventName).data(json));
        } catch (IOException e) {
            isClosed.set(true);
        }
    }

    private void sendError(SseEmitter emitter, String message) {
        try {
            String json = objectMapper.writeValueAsString(Map.of("error", message));
            emitter.send(SseEmitter.event().name("error").data(json));
        } catch (IOException e) {
            // Ignore
        }
    }

    private void safeSend(SseEmitter emitter, SseEmitter.SseEventBuilder event, AtomicBoolean isClosed) {
        if (isClosed.get()) return;
        try {
            emitter.send(event);
        } catch (IOException e) {
            isClosed.set(true);
        }
    }

    private void safeComplete(SseEmitter emitter, AtomicBoolean isClosed) {
        if (isClosed.getAndSet(true)) return;
        try {
            emitter.complete();
        } catch (Exception e) {
            // Already completed
        }
    }

    // ==================== Stream Processing Sub-methods ====================

    private static class StreamState {
        final AtomicReference<String> currentPhase;
        final StringBuilder accumulatedText;
        final StringBuilder reasoningBuffer;
        final AtomicBoolean hasReasoning;
        final List<Change> sentChanges;
        final AtomicBoolean changesStarted;

        StreamState(AtomicReference<String> currentPhase, StringBuilder accumulatedText,
                    StringBuilder reasoningBuffer, AtomicBoolean hasReasoning,
                    List<Change> sentChanges, AtomicBoolean changesStarted) {
            this.currentPhase = currentPhase;
            this.accumulatedText = accumulatedText;
            this.reasoningBuffer = reasoningBuffer;
            this.hasReasoning = hasReasoning;
            this.sentChanges = sentChanges;
            this.changesStarted = changesStarted;
        }
    }

    private void handleErrorResponse(Response response, SseEmitter emitter, AtomicBoolean isClosed) {
        String errorBody = "";
        try {
            if (response.body() != null) {
                errorBody = response.body().string();
            }
        } catch (IOException ignored) {}
        log.error("API 错误响应 (HTTP {}): {}", response.code(), errorBody);
        if (!isClosed.get()) {
            sendError(emitter, "校对服务暂时不可用，请稍后重试");
            safeComplete(emitter, isClosed);
        }
    }

    /**
     * 处理 R1 推理模型的 reasoning_content 数据块。
     */
    private void processReasoningChunk(String reasoning, StreamState state,
                                        SseEmitter emitter, AtomicBoolean isClosed) {
        state.reasoningBuffer.append(reasoning);
        state.hasReasoning.set(true);
        String rbText = state.reasoningBuffer.toString();
        int closeIdx = rbText.indexOf("</thinking>");

        // thinking 阶段：流式发送推理内容
        if ("thinking".equals(state.currentPhase.get())) {
            if (closeIdx >= 0) {
                String thinkingText = rbText.substring(0, closeIdx).trim();
                sendSseEvent(emitter, "thinking",
                        Map.of("text", thinkingText, "done", true), isClosed);
                state.currentPhase.set("correction");
            } else {
                sendSseEvent(emitter, "thinking",
                        Map.of("text", rbText, "done", false), isClosed);
            }
        }

        if (closeIdx < 0) return;
        String afterThinking = rbText.substring(closeIdx + "</thinking>".length()).trim();

        // correction 阶段
        if ("correction".equals(state.currentPhase.get())) {
            int corrOpen = afterThinking.indexOf("<correction>");
            int corrClose = afterThinking.indexOf("</correction>");
            if (corrOpen >= 0) {
                String correctionText = afterThinking.substring(corrOpen + "<correction>".length(),
                        corrClose >= 0 ? corrClose : afterThinking.length()).trim();
                if (!correctionText.isEmpty()) {
                    sendSseEvent(emitter, "correction",
                            Map.of("text", correctionText, "done", corrClose >= 0), isClosed);
                    if (corrClose >= 0) {
                        state.currentPhase.set("changes");
                    }
                }
            }
        }

        // changes 阶段
        if ("changes".equals(state.currentPhase.get())) {
            int chgOpen = afterThinking.indexOf("<changes>");
            int chgClose = afterThinking.indexOf("</changes>");
            if (chgOpen >= 0) {
                String changesText = afterThinking.substring(chgOpen + "<changes>".length(),
                        chgClose >= 0 ? chgClose : afterThinking.length()).trim();
                boolean changesDone = chgClose >= 0;
                if (!changesText.isEmpty() || changesDone) {
                    List<Change> changesList = parseChangesJson(changesText);
                    changesList = StreamingResponseParser.filterExampleChanges(changesList);
                    if (!changesList.isEmpty() || changesDone) {
                        sendSseEvent(emitter, "changes",
                                Map.of("changes", changesList, "done", changesDone), isClosed);
                        if (changesDone) {
                            state.currentPhase.set("done");
                        }
                    }
                }
            }
        }
    }

    /**
     * 处理标准模型的 content 数据块。
     */
    private void processContentChunk(String content, StreamState state,
                                      SseEmitter emitter, AtomicBoolean isClosed) {
        // R1 模型首个 content token：关闭 thinking 阶段
        if (state.hasReasoning.get() && "thinking".equals(state.currentPhase.get())) {
            String rbText = state.reasoningBuffer.toString();
            String thinkingText = rbText;
            int closeIdx = rbText.indexOf("</thinking>");
            if (closeIdx >= 0) {
                thinkingText = rbText.substring(0, closeIdx).trim();
            }
            sendSseEvent(emitter, "thinking",
                    Map.of("text", thinkingText, "done", true), isClosed);
            state.currentPhase.set("correction");
        }

        // 累积内容
        if (!state.hasReasoning.get()) {
            state.accumulatedText.append(content);
        } else {
            if ("correction".equals(state.currentPhase.get())
                    && !state.accumulatedText.toString().contains("<correction>")) {
                state.accumulatedText.append("<correction>");
            }
            state.accumulatedText.append(content);
        }

        ParsedResult parsed = StreamingResponseParser.parse(state.accumulatedText.toString());

        // thinking 阶段
        if ("thinking".equals(state.currentPhase.get()) && !parsed.getThinking().isEmpty()) {
            boolean done = parsed.isThinkingDone();
            sendSseEvent(emitter, "thinking",
                    Map.of("text", parsed.getThinking(), "done", done), isClosed);
            if (done) {
                log.debug("thinking 阶段完成");
                state.currentPhase.set("correction");
            }
        }

        // correction 阶段
        if ("correction".equals(state.currentPhase.get())) {
            if (!parsed.getCorrection().isEmpty()) {
                boolean done = parsed.isCorrectionDone();
                sendSseEvent(emitter, "correction",
                        Map.of("text", parsed.getCorrection(), "done", done), isClosed);
                if (done) {
                    state.currentPhase.set("changes");
                }
            } else if (parsed.isCorrectionDone()) {
                state.currentPhase.set("changes");
            }
        }

        // changes 阶段（流式增量发送）
        if ("changes".equals(state.currentPhase.get())) {
            boolean done = parsed.isChangesDone();
            List<Change> currentChanges = parsed.getChanges();

            if (currentChanges.size() > state.sentChanges.size()) {
                List<Change> newChanges = currentChanges.subList(
                        state.sentChanges.size(), currentChanges.size());
                for (Change newChange : newChanges) {
                    sendSseEvent(emitter, "change",
                            Map.of("change", newChange, "done", false), isClosed);
                    state.sentChanges.add(newChange);
                }
            }

            if (done) {
                sendSseEvent(emitter, "changes",
                        Map.of("changes", state.sentChanges, "done", true), isClosed);
                state.currentPhase.set("done");
            }
        }
    }

    /**
     * 流结束时的收尾处理：补齐未发送的事件。
     */
    private void handleStreamEnd(StreamState state, SseEmitter emitter, AtomicBoolean isClosed) {
        String reasoningStr = state.reasoningBuffer.toString();

        boolean reasoningHasXml = state.hasReasoning.get()
                && (reasoningStr.contains("<thinking>") || reasoningStr.contains("<correction>"))
                && (reasoningStr.contains("</thinking>") || reasoningStr.contains("<correction>"));
        boolean reasoningOnly = state.hasReasoning.get()
                && state.accumulatedText.length() == 0 && !reasoningStr.isEmpty();

        if ((reasoningHasXml || reasoningOnly) && state.accumulatedText.length() == 0) {
            // 推理模型：所有内容在 reasoning_content 中
            sendFinalReasoningEvents(reasoningStr, state, emitter, isClosed);
        } else {
            // 标准模型或混合模型
            sendFinalStandardEvents(state, emitter, isClosed);
        }

        sendSseEvent(emitter, "done", Map.of(), isClosed);
        safeComplete(emitter, isClosed);
    }

    private void sendFinalReasoningEvents(String reasoningStr, StreamState state,
                                           SseEmitter emitter, AtomicBoolean isClosed) {
        ParsedResult finalParsed = StreamingResponseParser.parse(reasoningStr);

        String thinkingText = finalParsed.getThinking();
        if (thinkingText.isEmpty() && !reasoningStr.isEmpty()) {
            thinkingText = reasoningStr.trim();
        }

        if (!thinkingText.isEmpty()) {
            sendSseEvent(emitter, "thinking",
                    Map.of("text", thinkingText, "done", true), isClosed);
        }
        if (!finalParsed.getCorrection().isEmpty()) {
            state.currentPhase.set("correction");
            sendSseEvent(emitter, "correction",
                    Map.of("text", finalParsed.getCorrection(), "done", true), isClosed);
        }
        state.currentPhase.set("changes");
        sendSseEvent(emitter, "changes",
                Map.of("changes", finalParsed.getChanges(), "done", true), isClosed);
        state.currentPhase.set("done");
    }

    private void sendFinalStandardEvents(StreamState state, SseEmitter emitter, AtomicBoolean isClosed) {
        // R1 模型：补齐未闭合的标签
        if (state.hasReasoning.get() && state.accumulatedText.toString().contains("<correction>")
                && !state.accumulatedText.toString().contains("</correction>")) {
            state.accumulatedText.append("</correction>");
        }
        if (state.hasReasoning.get() && !state.accumulatedText.toString().contains("<changes>")) {
            state.accumulatedText.append("<changes>[]</changes>");
        }

        if ("done".equals(state.currentPhase.get())) return;

        ParsedResult finalParsed = StreamingResponseParser.parse(state.accumulatedText.toString());

        if (!finalParsed.getThinking().isEmpty() && "thinking".equals(state.currentPhase.get())) {
            sendSseEvent(emitter, "thinking",
                    Map.of("text", finalParsed.getThinking(), "done", true), isClosed);
            state.currentPhase.set("correction");
        }
        if (!finalParsed.getCorrection().isEmpty() && "correction".equals(state.currentPhase.get())) {
            sendSseEvent(emitter, "correction",
                    Map.of("text", finalParsed.getCorrection(), "done", true), isClosed);
            state.currentPhase.set("changes");
        }
        if ("changes".equals(state.currentPhase.get())) {
            sendSseEvent(emitter, "changes",
                    Map.of("changes", finalParsed.getChanges(), "done", true), isClosed);
            state.currentPhase.set("done");
        }
    }

    /**
     * 解析 changes JSON 字符串为 Change 列表。
     */
    private List<Change> parseChangesJson(String changesText) {
        List<Change> changesList = new ArrayList<>();
        if (changesText.isEmpty()) return changesList;

        try {
            Object parsed = objectMapper.readValue(changesText, Object.class);
            if (parsed instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof java.util.Map<?,?> map
                            && map.containsKey("original") && map.containsKey("corrected")) {
                        changesList.add(objectMapper.convertValue(item, Change.class));
                    }
                }
            }
        } catch (Exception e) {
            changesList = StreamingResponseParser.extractCompleteObjects(changesText);
        }
        return changesList;
    }
}

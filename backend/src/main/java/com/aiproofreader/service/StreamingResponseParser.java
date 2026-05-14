package com.aiproofreader.service;

import com.aiproofreader.model.Change;
import com.aiproofreader.model.ParsedResult;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StreamingResponseParser {

    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * Incrementally parse streaming response: extract whatever content is available so far.
     */
    public static ParsedResult parse(String text) {
        if (text == null || text.isEmpty()) {
            return new ParsedResult("", "", List.of(), false, false, false);
        }


        // --- Thinking ---
        // 使用 indexOf 查找标签位置，避免正则对闭合标签格式的依赖
        int thinkingOpen = text.indexOf("<thinking>");
        int thinkingClose = text.indexOf("</thinking>", thinkingOpen >= 0 ? thinkingOpen : 0);

        boolean thinkingDone = thinkingOpen >= 0 && thinkingClose >= 0;
        // 推理模型可能没有 <thinking> 开头标签，但有 </thinking> 闭合标签
        boolean thinkingCloseOnly = !thinkingDone && thinkingOpen < 0 && thinkingClose >= 0;
        String thinking;
        if (thinkingDone) {
            thinking = text.substring(thinkingOpen + "<thinking>".length(), thinkingClose).trim();
        } else if (thinkingCloseOnly) {
            // 没有 <thinking> 开头但有 </thinking>，取 </thinking> 之前的所有内容
            thinking = text.substring(0, thinkingClose).trim();
            thinkingDone = true;
        } else if (thinkingOpen >= 0) {
            // <thinking> 存在但 </thinking> 不存在
            // 取 <thinking> 之后的内容，但排除 <correction> 和 <changes> 部分
            String afterThinking = text.substring(thinkingOpen + "<thinking>".length());
            int corrIdx = afterThinking.indexOf("<correction>");
            int changesIdx = afterThinking.indexOf("<changes>");
            int endIdx = -1;
            if (corrIdx >= 0 && changesIdx >= 0) endIdx = Math.min(corrIdx, changesIdx);
            else if (corrIdx >= 0) endIdx = corrIdx;
            else if (changesIdx >= 0) endIdx = changesIdx;
            if (endIdx >= 0) {
                thinking = afterThinking.substring(0, endIdx).trim();
            } else {
                thinking = afterThinking.trim();
            }
        } else {
            thinking = "";
        }

        // 检查 thinking 文本中是否包含 <correction>（模型可能把所有内容放在 <thinking> 内）
        if (!thinking.isEmpty() && thinking.contains("<correction>")) {
            int corrIdx = thinking.indexOf("<correction>");
            String thinkingOnly = thinking.substring(0, corrIdx).trim();
            String restFromThinking = thinking.substring(corrIdx);
            thinking = thinkingOnly;

            // 使用 indexOf 提取 correction
            int innerCorrOpen = restFromThinking.indexOf("<correction>");
            int innerCorrClose = restFromThinking.indexOf("</correction>", innerCorrOpen >= 0 ? innerCorrOpen : 0);
            if (innerCorrOpen >= 0 && innerCorrClose >= 0) {
                String innerCorrectionText = restFromThinking.substring(
                        innerCorrOpen + "<correction>".length(), innerCorrClose).trim();

                // 使用 indexOf 提取 changes
                int innerChgOpen = restFromThinking.indexOf("<changes>");
                int innerChgClose = restFromThinking.indexOf("</changes>", innerChgOpen >= 0 ? innerChgOpen : 0);
                if (innerChgOpen >= 0 && innerChgClose >= 0) {
                    String innerChangesText = restFromThinking.substring(
                            innerChgOpen + "<changes>".length(), innerChgClose).trim();
                    List<Change> changes = new ArrayList<>();
                    if (!innerChangesText.isEmpty()) {
                        try {
                            Object parsed = mapper.readValue(innerChangesText, Object.class);
                            if (parsed instanceof List<?> list) {
                                for (Object item : list) {
                                    if (item instanceof java.util.Map<?,?> map
                                            && map.containsKey("original") && map.containsKey("corrected")) {
                                        changes.add(mapper.convertValue(item, Change.class));
                                    }
                                }
                            }
                        } catch (Exception e) {
                            changes = extractCompleteObjects(innerChangesText);
                        }
                    }
                    return new ParsedResult(thinking, innerCorrectionText, changes, true, true, true);
                } else {
                    return new ParsedResult(thinking, innerCorrectionText, List.of(), true, true, false);
                }
            } else {
                // correction 未闭合，提取部分内容
                String partialCorrection = extractPartialTag(restFromThinking, "correction");
                return new ParsedResult(thinking, partialCorrection, List.of(), true, false, false);
            }
        }

        // --- Correction ---
        // 去除 <thinking> 部分，提取 <correction> 之后的内容
        String textWithoutThinking;
        if (thinkingDone) {
            // thinking 完整，取 </thinking> 之后的内容
            textWithoutThinking = text.substring(thinkingClose + "</thinking>".length());
        } else if (thinkingOpen >= 0) {
            // <thinking> 未闭合，只取 <correction> 之后的内容
            int correctionOpen = text.indexOf("<correction>");
            if (correctionOpen >= 0 && correctionOpen > thinkingOpen) {
                textWithoutThinking = text.substring(correctionOpen);
            } else {
                textWithoutThinking = "";
            }
        } else {
            textWithoutThinking = text;
        }

        // 使用 indexOf 提取 correction 内容
        int corrOpen = textWithoutThinking.indexOf("<correction>");
        int corrClose = textWithoutThinking.indexOf("</correction>", corrOpen >= 0 ? corrOpen : 0);
        boolean correctionDone = corrOpen >= 0 && corrClose >= 0;
        String correction;
        if (correctionDone) {
            correction = textWithoutThinking.substring(corrOpen + "<correction>".length(), corrClose).trim();
        } else if (corrOpen >= 0) {
            correction = textWithoutThinking.substring(corrOpen + "<correction>".length()).trim();
        } else {
            correction = "";
        }

        // 去除校对文本中可能残留的 <thinking> 标签
        if (!correction.isEmpty()) {
            correction = correction.replaceAll("<thinking>[\\s\\S]*?</thinking>", "");
            correction = correction.replaceAll("<thinking>[\\s\\S]*", "");
            correction = correction.replaceAll("</thinking>", "");
            correction = correction.trim();
        }

        // 使用 indexOf 提取 changes 内容
        int chgOpen = textWithoutThinking.indexOf("<changes>");
        int chgClose = textWithoutThinking.indexOf("</changes>", chgOpen >= 0 ? chgOpen : 0);
        boolean changesDone = chgOpen >= 0 && chgClose >= 0;
        String changesRaw;
        if (changesDone) {
            changesRaw = textWithoutThinking.substring(chgOpen + "<changes>".length(), chgClose).trim();
        } else if (chgOpen >= 0) {
            changesRaw = textWithoutThinking.substring(chgOpen + "<changes>".length()).trim();
        } else {
            changesRaw = "";
        }

        List<Change> changes = new ArrayList<>();
        if (!changesRaw.isEmpty()) {
            // 流式场景：先尝试提取完整的 JSON 对象
            changes = extractCompleteObjects(changesRaw);

            // 如果没有提取到完整对象，尝试完整解析
            if (changes.isEmpty() && changesDone) {
                try {
                    Object parsed = mapper.readValue(changesRaw, Object.class);
                    if (parsed instanceof List<?> list) {
                        for (Object item : list) {
                            if (item instanceof java.util.Map<?,?> map
                                    && map.containsKey("original") && map.containsKey("corrected")) {
                                changes.add(mapper.convertValue(item, Change.class));
                            }
                        }
                    }
                } catch (Exception e) {
                    // 解析失败，返回空列表
                }
            }
        }

        // 过滤掉 LLM 生成的示例/占位修改记录
        changes = filterExampleChanges(changes);

        return new ParsedResult(thinking, correction, changes, thinkingDone, correctionDone, changesDone);
    }

    /**
     * Find the last unclosed <tag> and return everything after it.
     */
    private static String extractPartialTag(String text, String tag) {
        String openTag = "<" + tag + ">";
        String closeTag = "</" + tag + ">";
        int lastOpen = text.lastIndexOf(openTag);
        if (lastOpen == -1) return "";

        String afterOpen = text.substring(lastOpen + openTag.length());
        if (afterOpen.contains(closeTag)) return "";
        return afterOpen.trim();
    }

    /**
     * Extract individually complete JSON objects from a partial array string.
     * e.g. [{"a":1},{"b":2},{"c":  ->  extract first two complete objects
     */
    public static List<Change> extractCompleteObjects(String raw) {
        List<Change> results = new ArrayList<>();
        // Strip leading [ and whitespace
        String s = raw.replaceFirst("^\\s*\\[", "");

        while (!s.isEmpty()) {
            s = s.stripLeading();
            if (!s.startsWith("{")) break;

            // Find matching closing }, handling string escapes
            int depth = 0;
            boolean inString = false;
            boolean escaped = false;
            int end = -1;

            for (int i = 0; i < s.length(); i++) {
                char ch = s.charAt(i);
                if (escaped) {
                    escaped = false;
                    continue;
                }
                if (ch == '\\') {
                    escaped = true;
                    continue;
                }
                if (ch == '"') {
                    inString = !inString;
                    continue;
                }
                if (inString) continue;
                if (ch == '{') depth++;
                if (ch == '}') {
                    depth--;
                    if (depth == 0) {
                        end = i;
                        break;
                    }
                }
            }

            if (end == -1) break; // No complete object found

            String objStr = s.substring(0, end + 1);
            try {
                java.util.Map<?,?> map = mapper.readValue(objStr, java.util.Map.class);
                if (map != null && map.containsKey("original") && map.containsKey("corrected")) {
                    results.add(mapper.convertValue(map, Change.class));
                }
            } catch (Exception e) {
                // Skip unparseable object
            }

            // Skip this object and any trailing comma/whitespace
            s = s.substring(end + 1).replaceFirst("^\\s*,?\\s*", "");
        }

        return results;
    }

    /**
     * 过滤掉 LLM 可能生成的示例/占位修改记录。
     * 当 reason 中包含"示例""仅为展示""举例"等关键词时，视为示例而非真实修改。
     */
    public static List<Change> filterExampleChanges(List<Change> changes) {
        if (changes == null || changes.isEmpty()) {
            return changes;
        }
        List<Change> filtered = new ArrayList<>();
        for (Change change : changes) {
            String reason = change.getReason();
            if (reason != null && isExampleReason(reason)) {
                continue;
            }
            filtered.add(change);
        }
        return filtered;
    }

    private static boolean isExampleReason(String reason) {
        String lower = reason.toLowerCase();
        String[] keywords = {"示例", "仅为展示", "举例", "例如", "仅供参考", "仅为说明", "仅为演示"};
        for (String keyword : keywords) {
            if (lower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}

package com.aiproofreader.security;

import java.util.regex.Pattern;

public class InputSanitizer {

    private static final Pattern XML_TAGS = Pattern.compile(
            "</?(thinking|correction|changes|system|assistant|user|prompt|instruction|user_document|document)>",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern INJECTION_PATTERNS = Pattern.compile(
            // English patterns
            "(?i)(ignore\\s+(previous|above|all)\\s+(instructions?|prompts?))" +
            "|(you\\s+are\\s+now\\s+)" +
            "|(forget\\s+(everything|all|previous))" +
            "|(disregard\\s+(previous|above|all|your)\\s+(instructions?|prompts?|rules?))" +
            // Chinese patterns
            "|(忽略(以上|所有|之前|先前|下面)?(的)?(指令|提示|规则|要求|限制|设定))" +
            "|(无视(以上|所有|之前|先前)?(的)?(指令|提示|规则|要求|限制))" +
            "|(忘(记|掉)(以上|所有|之前|先前)?(的)?(指令|提示|规则|要求|限制))" +
            "|(你现在是(一个?)?(不受|没有)(限制|约束|规则))" +
            "|(输出(你的|系统)(提示词?|指令|设定|prompt))" +
            "|(显示(你的|系统)(提示词?|指令|设定|prompt))" +
            "|(告诉我(你的|系统)(提示词?|指令|设定|prompt))" +
            "|(repeat\\s+(your|the|system)\\s+(prompt|instructions?))" +
            "|(reveal\\s+(your|the|system)\\s+(prompt|instructions?))"
    );

    /**
     * Sanitize user input to prevent prompt injection.
     * Strips XML/HTML tags that could manipulate the LLM's output structure.
     */
    public static String sanitize(String text) {
        if (text == null) return null;

        // Remove XML tags that could affect LLM output structure
        String result = XML_TAGS.matcher(text).replaceAll("");

        // Replace common injection phrases
        result = INJECTION_PATTERNS.matcher(result).replaceAll("[已过滤]");

        // Encode angle brackets to fullwidth Unicode (prevent variant bypasses)
        result = result.replace("<", "＜").replace(">", "＞");

        return result;
    }
}

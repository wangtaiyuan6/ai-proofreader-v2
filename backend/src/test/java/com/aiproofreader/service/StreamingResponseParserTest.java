package com.aiproofreader.service;

import com.aiproofreader.model.Change;
import com.aiproofreader.model.ParsedResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StreamingResponseParserTest {

    // ==================== parse() tests ====================

    @Test
    void parse_nullInput_returnsEmptyResult() {
        ParsedResult result = StreamingResponseParser.parse(null);
        assertThat(result.getThinking()).isEmpty();
        assertThat(result.getCorrection()).isEmpty();
        assertThat(result.getChanges()).isEmpty();
        assertThat(result.isThinkingDone()).isFalse();
        assertThat(result.isCorrectionDone()).isFalse();
        assertThat(result.isChangesDone()).isFalse();
    }

    @Test
    void parse_emptyString_returnsEmptyResult() {
        ParsedResult result = StreamingResponseParser.parse("");
        assertThat(result.getThinking()).isEmpty();
        assertThat(result.getCorrection()).isEmpty();
        assertThat(result.getChanges()).isEmpty();
    }

    @Test
    void parse_standardModel_allThreeTags() {
        String input = """
                <thinking>
                文档体裁分析：测试文档。
                目标受众：开发者。
                正式程度：正式。
                校验规则：
                1. 检查错别字：检查常见错别字。
                </thinking>

                <correction>
                这是校对后的文档内容。
                </correction>

                <changes>
                [{"original":"以经","corrected":"已经","reason":"错别字"}]
                </changes>
                """;

        ParsedResult result = StreamingResponseParser.parse(input);

        assertThat(result.isThinkingDone()).isTrue();
        assertThat(result.getThinking()).contains("文档体裁分析");
        assertThat(result.isCorrectionDone()).isTrue();
        assertThat(result.getCorrection()).isEqualTo("这是校对后的文档内容。");
        assertThat(result.isChangesDone()).isTrue();
        assertThat(result.getChanges()).hasSize(1);
        assertThat(result.getChanges().get(0).getOriginal()).isEqualTo("以经");
        assertThat(result.getChanges().get(0).getCorrected()).isEqualTo("已经");
    }

    @Test
    void parse_standardModel_emptyChanges() {
        String input = """
                <thinking>分析</thinking>
                <correction>文档内容</correction>
                <changes>[]</changes>
                """;

        ParsedResult result = StreamingResponseParser.parse(input);

        assertThat(result.isChangesDone()).isTrue();
        assertThat(result.getChanges()).isEmpty();
    }

    @Test
    void parse_r1Model_noThinkingOpenTag() {
        // R1 模型可能省略 <thinking> 开头标签
        String input = """
                文档体裁分析：测试文档。
                目标受众：开发者。
                </thinking>
                <correction>校对后内容</correction>
                <changes>[]</changes>
                """;

        ParsedResult result = StreamingResponseParser.parse(input);

        assertThat(result.isThinkingDone()).isTrue();
        assertThat(result.getThinking()).contains("文档体裁分析");
        assertThat(result.isCorrectionDone()).isTrue();
        assertThat(result.getCorrection()).isEqualTo("校对后内容");
    }

    @Test
    void parse_thinkingOpenButNoClose() {
        String input = "<thinking>正在分析文档内容...";

        ParsedResult result = StreamingResponseParser.parse(input);

        assertThat(result.isThinkingDone()).isFalse();
        assertThat(result.getThinking()).isEqualTo("正在分析文档内容...");
        assertThat(result.getCorrection()).isEmpty();
    }

    @Test
    void parse_correctionInsideThinking() {
        // 模型把所有内容放在 <thinking> 内部
        String input = """
                <thinking>
                分析过程
                <correction>校对后内容</correction>
                <changes>[{"original":"错","corrected":"对","reason":"修正"}]</changes>
                </thinking>
                """;

        ParsedResult result = StreamingResponseParser.parse(input);

        assertThat(result.isThinkingDone()).isTrue();
        assertThat(result.getThinking()).isEqualTo("分析过程");
        assertThat(result.isCorrectionDone()).isTrue();
        assertThat(result.getCorrection()).isEqualTo("校对后内容");
        assertThat(result.isChangesDone()).isTrue();
        assertThat(result.getChanges()).hasSize(1);
    }

    @Test
    void parse_partialStreaming_thinkingOnly() {
        String input = "<thinking>部分分析内容";

        ParsedResult result = StreamingResponseParser.parse(input);

        assertThat(result.isThinkingDone()).isFalse();
        assertThat(result.getThinking()).isEqualTo("部分分析内容");
        assertThat(result.isCorrectionDone()).isFalse();
        assertThat(result.isChangesDone()).isFalse();
    }

    @Test
    void parse_partialStreaming_thinkingClosedCorrectionPartial() {
        String input = "<thinking>分析</thinking><correction>部分校对";

        ParsedResult result = StreamingResponseParser.parse(input);

        assertThat(result.isThinkingDone()).isTrue();
        assertThat(result.getThinking()).isEqualTo("分析");
        assertThat(result.isCorrectionDone()).isFalse();
        assertThat(result.getCorrection()).isEqualTo("部分校对");
        assertThat(result.isChangesDone()).isFalse();
    }

    @Test
    void parse_partialStreaming_correctionClosedChangesPartial() {
        String input = "<thinking>分析</thinking><correction>校对</correction><changes>[{\"original\":\"a\",\"corrected\":\"b\",\"reason\":\"r\"}";

        ParsedResult result = StreamingResponseParser.parse(input);

        assertThat(result.isThinkingDone()).isTrue();
        assertThat(result.isCorrectionDone()).isTrue();
        assertThat(result.isChangesDone()).isFalse();
        // 应该能提取到一个完整的 change 对象
        assertThat(result.getChanges()).hasSize(1);
    }

    @Test
    void parse_correctionContainsThinkingRemnants() {
        String input = """
                <thinking>分析</thinking>
                <correction><thinking>残留标签</thinking>校对后内容</correction>
                <changes>[]</changes>
                """;

        ParsedResult result = StreamingResponseParser.parse(input);

        assertThat(result.getCorrection()).isEqualTo("校对后内容");
        assertThat(result.getCorrection()).doesNotContain("<thinking>");
    }

    @Test
    void parse_thinkingOpenThenCorrectionWithoutClose() {
        // <thinking> 未闭合，但 <correction> 已出现
        String input = "<thinking>分析<correction>校对内容</correction><changes>[]</changes>";

        ParsedResult result = StreamingResponseParser.parse(input);

        assertThat(result.getThinking()).isEqualTo("分析");
        assertThat(result.isCorrectionDone()).isTrue();
        assertThat(result.getCorrection()).isEqualTo("校对内容");
    }

    @Test
    void parse_multipleChanges() {
        String input = """
                <thinking>分析</thinking>
                <correction>校对后</correction>
                <changes>[{"original":"以经","corrected":"已经","reason":"错别字"},{"original":"在见","corrected":"再见","reason":"错别字"}]</changes>
                """;

        ParsedResult result = StreamingResponseParser.parse(input);

        assertThat(result.getChanges()).hasSize(2);
        assertThat(result.getChanges().get(0).getOriginal()).isEqualTo("以经");
        assertThat(result.getChanges().get(1).getOriginal()).isEqualTo("在见");
    }

    // ==================== extractCompleteObjects() tests ====================

    @Test
    void extractCompleteObjects_fullValidArray() {
        String raw = "[{\"original\":\"a\",\"corrected\":\"b\",\"reason\":\"r\"},{\"original\":\"c\",\"corrected\":\"d\",\"reason\":\"r2\"}]";

        List<Change> changes = StreamingResponseParser.extractCompleteObjects(raw);

        assertThat(changes).hasSize(2);
        assertThat(changes.get(0).getOriginal()).isEqualTo("a");
        assertThat(changes.get(1).getCorrected()).isEqualTo("d");
    }

    @Test
    void extractCompleteObjects_partialArray_lastObjectIncomplete() {
        String raw = "[{\"original\":\"a\",\"corrected\":\"b\",\"reason\":\"r\"},{\"original\":\"c\",\"corrected\":";

        List<Change> changes = StreamingResponseParser.extractCompleteObjects(raw);

        assertThat(changes).hasSize(1);
        assertThat(changes.get(0).getOriginal()).isEqualTo("a");
    }

    @Test
    void extractCompleteObjects_emptyArray() {
        List<Change> changes = StreamingResponseParser.extractCompleteObjects("[]");
        assertThat(changes).isEmpty();
    }

    @Test
    void extractCompleteObjects_singleObject() {
        String raw = "[{\"original\":\"x\",\"corrected\":\"y\",\"reason\":\"z\"}]";

        List<Change> changes = StreamingResponseParser.extractCompleteObjects(raw);

        assertThat(changes).hasSize(1);
        assertThat(changes.get(0).getReason()).isEqualTo("z");
    }

    @Test
    void extractCompleteObjects_missingCorrectedField() {
        String raw = "[{\"original\":\"a\",\"foo\":\"bar\"}]";

        List<Change> changes = StreamingResponseParser.extractCompleteObjects(raw);

        assertThat(changes).isEmpty();
    }

    @Test
    void extractCompleteObjects_withEscapedQuotes() {
        String raw = "[{\"original\":\"他说\\\"你好\\\"\",\"corrected\":\"他说'你好'\",\"reason\":\"引号\"}]";

        List<Change> changes = StreamingResponseParser.extractCompleteObjects(raw);

        assertThat(changes).hasSize(1);
        assertThat(changes.get(0).getOriginal()).contains("你好");
    }

    // ==================== filterExampleChanges() tests ====================

    @Test
    void filterExampleChanges_nullInput() {
        assertThat(StreamingResponseParser.filterExampleChanges(null)).isNull();
    }

    @Test
    void filterExampleChanges_emptyList() {
        assertThat(StreamingResponseParser.filterExampleChanges(List.of())).isEmpty();
    }

    @Test
    void filterExampleChanges_filtersExampleReasons() {
        List<Change> changes = List.of(
                new Change("a", "b", "这是示例修改"),
                new Change("c", "d", "仅为展示"),
                new Change("e", "f", "举例说明"),
                new Change("g", "h", "例如这样"),
                new Change("i", "j", "仅供参考"),
                new Change("k", "l", "仅为说明"),
                new Change("m", "n", "仅为演示")
        );

        List<Change> filtered = StreamingResponseParser.filterExampleChanges(changes);

        assertThat(filtered).isEmpty();
    }

    @Test
    void filterExampleChanges_keepsRealChanges() {
        List<Change> changes = List.of(
                new Change("以经", "已经", "错别字修正"),
                new Change("在见", "再见", "用词不当")
        );

        List<Change> filtered = StreamingResponseParser.filterExampleChanges(changes);

        assertThat(filtered).hasSize(2);
    }

    @Test
    void filterExampleChanges_mixed() {
        List<Change> changes = List.of(
                new Change("以经", "已经", "错别字修正"),
                new Change("a", "b", "这是示例"),
                new Change("在见", "再见", "用词不当")
        );

        List<Change> filtered = StreamingResponseParser.filterExampleChanges(changes);

        assertThat(filtered).hasSize(2);
        assertThat(filtered.get(0).getOriginal()).isEqualTo("以经");
        assertThat(filtered.get(1).getOriginal()).isEqualTo("在见");
    }

    @Test
    void filterExampleChanges_nullReason_kept() {
        List<Change> changes = List.of(new Change("a", "b", null));

        List<Change> filtered = StreamingResponseParser.filterExampleChanges(changes);

        assertThat(filtered).hasSize(1);
    }
}

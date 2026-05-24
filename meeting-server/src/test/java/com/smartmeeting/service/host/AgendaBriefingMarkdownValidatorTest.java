package com.smartmeeting.service.host;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgendaBriefingMarkdownValidatorTest {

    @Test
    @DisplayName("正常 Markdown 通报通过")
    void acceptsValidMarkdown() {
        String md = """
                # 前期事项进度通报

                ## 延期事项

                - **事项A**：负责人 张三
                """;
        assertThat(AgendaBriefingMarkdownValidator.isValid(md)).isTrue();
        assertThat(AgendaBriefingMarkdownValidator.rejectReason(md)).isNull();
    }

    @Test
    @DisplayName("拒绝纪要增强 JSON")
    void rejectsMinuteEnhancementJson() {
        String json = "{\"optimized_minute\":\"x\",\"quality_check\":{\"score\":0}}";
        assertThat(AgendaBriefingMarkdownValidator.isValid(json)).isFalse();
        assertThat(AgendaBriefingMarkdownValidator.rejectReason(json)).isEqualTo("minute_enhancement_json");
    }

    @Test
    @DisplayName("拒绝 Agent 缓存话术")
    void rejectsCacheMeta() {
        String meta = "用户发送了 /skill:matter-progress 指令。我之前已经有缓存数据，直接使用缓存";
        assertThat(AgendaBriefingMarkdownValidator.isValid(meta)).isFalse();
        assertThat(AgendaBriefingMarkdownValidator.rejectReason(meta)).isEqualTo("agent_cache_meta");
    }

    @Test
    @DisplayName("过短且无标题拒绝")
    void rejectsTooShort() {
        assertThat(AgendaBriefingMarkdownValidator.rejectReason("简短说明")).isEqualTo("too_short");
    }
}

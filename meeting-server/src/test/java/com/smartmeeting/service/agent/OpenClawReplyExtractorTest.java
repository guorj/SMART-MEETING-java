package com.smartmeeting.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenClawReplyExtractorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("纯 Markdown 正文直接返回")
    void plainMarkdown() {
        String md = "# 事项进度通报\n\n| 状态 | 数量 |";
        assertThat(OpenClawReplyExtractor.extractFromBody(md)).contains("事项进度通报");
    }

    @Test
    @DisplayName("reply 字段 JSON")
    void replyField() throws Exception {
        String json = "{\"reply\":\"# 通报\\n\\n内容\"}";
        assertThat(OpenClawReplyExtractor.extractFromBody(json)).contains("通报");
    }

    @Test
    @DisplayName("payloads 格式")
    void payloadsFormat() throws Exception {
        String json = """
                {"status":"ok","result":{"payloads":[{"text":"# 通报\\n\\n分项进度良好"}]}}
                """;
        assertThat(OpenClawReplyExtractor.extractFromBody(json)).contains("分项进度良好");
    }

    @Test
    @DisplayName("WS chat message 为对象（content 数组）")
    void assistantMessageObject() throws Exception {
        String json = """
                {
                  "message": {
                    "role": "assistant",
                    "content": [
                      {"type": "text", "text": "# 事项进度通报\\n\\n总体概览正常"}
                    ]
                  }
                }
                """;
        assertThat(OpenClawReplyExtractor.extractFromJson(objectMapper.readTree(json)))
                .contains("事项进度通报");
    }

    @Test
    @DisplayName("无法识别的 JSON 返回 null")
    void unknownJsonWithoutText() {
        assertThat(OpenClawReplyExtractor.extractFromBody("{\"meta\":{\"ok\":true}}")).isNull();
    }
}

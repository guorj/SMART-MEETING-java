package com.smartmeeting.matterprogress.openclaw;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OpenClawChatHistoryTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void extractNewAssistantText_returnsOnlyMessagesAfterBaseline() throws Exception {
        String json = """
                [
                  {"role":"user","content":"old"},
                  {"role":"assistant","content":"first reply"},
                  {"role":"user","content":"new task"},
                  {"role":"assistant","content":"generatedReportUrl=https://x.feishu.cn/docx/abc"}
                ]
                """;
        var messages = mapper.readTree(json);
        assertEquals(2, OpenClawChatHistory.countAssistantMessages(messages));
        assertEquals("generatedReportUrl=https://x.feishu.cn/docx/abc",
                OpenClawChatHistory.extractLatestAssistantText(messages));
        assertEquals("generatedReportUrl=https://x.feishu.cn/docx/abc",
                OpenClawChatHistory.extractNewAssistantText(messages, 1));
        assertNull(OpenClawChatHistory.extractNewAssistantText(messages, 2));
    }

    @Test
    void extractNewAssistantText_nullWhenNoNewMessage() throws Exception {
        String json = """
                [{"role":"assistant","content":"only one"}]
                """;
        assertNull(OpenClawChatHistory.extractNewAssistantText(mapper.readTree(json), 1));
    }
}

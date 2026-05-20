package com.smartmeeting.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.BaseTest;
import com.smartmeeting.api.dto.MeetingCreateRequest;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.enums.MeetingStatus;
import com.smartmeeting.enums.MinuteGenerationStatus;
import com.smartmeeting.service.MeetingMinuteService;
import com.smartmeeting.util.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 会议纪要 API 集成测试：验证 GET /minute 的数据来源与 doc_url 回传逻辑。
 */
class MeetingMinuteApiTest extends BaseTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private MeetingMinuteService meetingMinuteService;

    /** 库内存在 Markdown 正文时应返回 source=database 及 docUrl。 */
    @Test
    @DisplayName("GET /minute 返回库内 Markdown 与 doc_url")
    void getMinuteReturnsDatabaseAndDocUrl() throws Exception {
        MeetingCreateRequest request = new MeetingCreateRequest();
        request.setTitle("纪要 API 测试");
        request.setCompany("集团");
        request.setGroupName("组");

        String createResp = mockMvc.perform(post("/api/v1/meetings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String meetingId = objectMapper.readTree(createResp).path("data").path("id").asText();

        Meeting meeting = meetingMapper.selectById(meetingId);
        meeting.setDocUrl("https://example.feishu.cn/docx/test-token");
        meeting.setDocToken("test-token");
        meeting.setStatus(MeetingStatus.COMPLETED.name());
        meetingMapper.updateById(meeting);

        meetingMinuteService.saveLatest(meetingId, "## 纪要正文\n\n测试", MinuteGenerationStatus.READY);

        String token = jwtUtil.generateToken(meetingId, Map.of("meetingId", meetingId));
        mockMvc.perform(get("/api/v1/meetings/{id}/minute", meetingId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.source").value("database"))
                .andExpect(jsonPath("$.data.contentMarkdown").isNotEmpty())
                .andExpect(jsonPath("$.data.docUrl").value("https://example.feishu.cn/docx/test-token"))
                .andExpect(jsonPath("$.data.hasMinute").value(true));
    }

    /** 无库内正文仅有 doc_url 时 source 应为 feishu_only。 */
    @Test
    @DisplayName("GET /minute 无库内正文仅有 doc_url 时为 feishu_only")
    void getMinuteFeishuOnly() throws Exception {
        Meeting m = new Meeting();
        m.setId(java.util.UUID.randomUUID().toString());
        m.setTitle("仅飞书");
        m.setCompany("集团");
        m.setGroupName("组");
        m.setStatus(MeetingStatus.COMPLETED.name());
        m.setCreatorId("u1");
        m.setDocUrl("https://example.feishu.cn/docx/only-feishu");
        m.setCreatedAt(LocalDateTime.now());
        m.setUpdatedAt(LocalDateTime.now());
        meetingMapper.insert(m);

        mockMvc.perform(get("/api/v1/meetings/{id}/minute", m.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("feishu_only"))
                .andExpect(jsonPath("$.data.contentMarkdown").doesNotExist())
                .andExpect(jsonPath("$.data.hasMinute").value(true));
    }
}

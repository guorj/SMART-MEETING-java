package com.smartmeeting.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.api.dto.MeetingCreateRequest;
import com.smartmeeting.util.JwtUtil;
import org.junit.jupiter.api.*;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import com.smartmeeting.BaseTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * {@code MeetingController} REST API 集成测试：覆盖会议 CRUD、启动/结束、预设与健康检查等端点。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MeetingControllerTest extends BaseTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtil jwtUtil;

    private String createMeetingAndGetId() throws Exception {
        MeetingCreateRequest request = new MeetingCreateRequest();
        request.setTitle("控制器测试会议");
        request.setCompany("集团总部");
        request.setGroupName("测试组");

        String response = mockMvc.perform(post("/api/v1/meetings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).path("data").path("id").asText();
    }

    /** 创建会议应返回 200 及 ISSUE_COLLECTING 状态。 */
    @Test
    @Order(1)
    @DisplayName("POST /api/v1/meetings - 创建会议")
    void testCreateMeeting() throws Exception {
        String response = mockMvc.perform(post("/api/v1/meetings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.title").value("控制器测试会议"))
                .andExpect(jsonPath("$.data.status").value("ISSUE_COLLECTING"))
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        String id = json.path("data").path("id").asText();
        Assertions.assertFalse(id.isEmpty(), "Meeting ID should not be empty");
    }

    /** 空请求体创建会议应返回 400 校验错误。 */
    @Test
    @Order(2)
    @DisplayName("POST /api/v1/meetings - 验证失败")
    void testCreateMeetingValidationFail() throws Exception {
        mockMvc.perform(post("/api/v1/meetings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    /** 启动会议后状态应变为 STARTED。 */
    @Test
    @Order(3)
    @DisplayName("POST /api/v1/meetings/{id}/start - 启动会议")
    void testStartMeeting() throws Exception {
        String id = createMeetingAndGetId();

        mockMvc.perform(post("/api/v1/meetings/{id}/start", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("STARTED"));
    }

    /** 结束会议后状态应变为 PROCESSING 并返回时长。 */
    @Test
    @Order(4)
    @DisplayName("POST /api/v1/meetings/{id}/end - 结束会议")
    void testEndMeeting() throws Exception {
        String id = createMeetingAndGetId();
        mockMvc.perform(post("/api/v1/meetings/{id}/start", id)).andReturn();

        mockMvc.perform(post("/api/v1/meetings/{id}/end", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PROCESSING"))
                .andExpect(jsonPath("$.data.durationSeconds").isNumber());
    }

    /** 按 ID 查询会议应返回对应详情。 */
    @Test
    @Order(5)
    @DisplayName("GET /api/v1/meetings/{id} - 查询详情")
    void testGetMeeting() throws Exception {
        String id = createMeetingAndGetId();

        mockMvc.perform(get("/api/v1/meetings/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id));
    }

    /** 列表查询应返回会议数组。 */
    @Test
    @Order(6)
    @DisplayName("GET /api/v1/meetings - 列表查询")
    void testListMeetings() throws Exception {
        createMeetingAndGetId();
        createMeetingAndGetId();

        mockMvc.perform(get("/api/v1/meetings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    /** 查询不存在的会议应返回 404 业务码。 */
    @Test
    @Order(7)
    @DisplayName("GET /api/v1/meetings/{id} - 404")
    void testGetMeetingNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/meetings/non-existent-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    /** 会议类型预设接口应返回 6 种固定预设。 */
    @Test
    @Order(8)
    @DisplayName("GET /api/v1/meeting-type-presets - 固定会务预设")
    void testMeetingPresets() throws Exception {
        mockMvc.perform(get("/api/v1/meeting-type-presets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(6))
                .andExpect(jsonPath("$.data[0].code").value(1))
                .andExpect(jsonPath("$.data[5].code").value(6));
    }

    /** 健康检查端点应返回 code=0。 */
    @Test
    @Order(9)
    @DisplayName("GET /api/v1/health - 健康检查")
    void testHealth() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    /** 录音页 JWT 结束会话应将 STARTED 会议转为 PROCESSING。 */
    @Test
    @Order(10)
    @DisplayName("POST /api/v1/meetings/{id}/recording-session/end - 录音页 JWT 结束（未开麦仅 STARTED）")
    void testRecordingSessionEnd() throws Exception {
        String id = createMeetingAndGetId();
        mockMvc.perform(post("/api/v1/meetings/{id}/start", id)).andReturn();
        String recJwt = jwtUtil.generateToken(id, Map.of("meetingId", id, "type", "recording"));

        mockMvc.perform(post("/api/v1/meetings/{id}/recording-session/end", id)
                        .header("Authorization", "Bearer " + recJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("PROCESSING"));
    }

    /** 飞书 Web 入口 JWT 创建并启动会议应返回 STARTED 及录音链接。 */
    @Test
    @Order(11)
    @DisplayName("POST /api/v1/meetings/feishu-web/create-and-start - 带入口 JWT 创建并启动")
    void testFeishuWebCreateAndStart() throws Exception {
        String token = jwtUtil.generateFeishuWebStartMeetingEntryToken("ou_test_web", "oc_test_web");
        MeetingCreateRequest body = new MeetingCreateRequest();
        body.setPresetTypeCode(1);

        mockMvc.perform(post("/api/v1/meetings/feishu-web/create-and-start")
                        .header("X-Feishu-Web-Entry-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("STARTED"))
                .andExpect(jsonPath("$.data.creatorId").value("ou_test_web"))
                .andExpect(jsonPath("$.data.chatId").value("oc_test_web"))
                .andExpect(jsonPath("$.data.recordingUrl").isNotEmpty());
    }

    private MeetingCreateRequest createRequest() {
        MeetingCreateRequest request = new MeetingCreateRequest();
        request.setTitle("控制器测试会议");
        request.setCompany("集团总部");
        request.setGroupName("测试组");
        return request;
    }
}

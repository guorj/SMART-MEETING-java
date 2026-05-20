package com.smartmeeting.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.api.dto.MeetingCreateRequest;
import com.smartmeeting.api.dto.TodoAssignRequest;
import com.smartmeeting.api.dto.TodoStatusUpdateRequest;
import com.smartmeeting.BaseTest;
import com.smartmeeting.entity.MeetingTodo;
import com.smartmeeting.entity.Participant;
import com.smartmeeting.enums.Priority;
import com.smartmeeting.enums.TodoStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 待办 API 集成测试：覆盖列表、看板、状态更新、指派与异常场景。
 */
class TodoApiTest extends BaseTest {

    @Autowired
    private ObjectMapper objectMapper;

    private String createMeetingId() throws Exception {
        MeetingCreateRequest request = new MeetingCreateRequest();
        request.setTitle("待办 API 测试");
        request.setCompany("集团");
        request.setGroupName("组");
        request.setCreatorId("creator-1");
        MeetingCreateRequest.ParticipantEntry p = new MeetingCreateRequest.ParticipantEntry();
        p.setUserId("user-a");
        p.setName("张三");
        request.setParticipants(List.of(p));

        String response = mockMvc.perform(post("/api/v1/meetings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("id").asText();
    }

    private MeetingTodo insertTodo(String meetingId, String assigneeId) {
        MeetingTodo todo = new MeetingTodo();
        todo.setId(UUID.randomUUID().toString());
        todo.setMeetingId(meetingId);
        todo.setContent("跟进事项");
        todo.setAssigneeId(assigneeId);
        todo.setAssigneeName("张三");
        todo.setStatus(TodoStatus.PENDING.name());
        todo.setPriority(Priority.MEDIUM.name());
        todo.setRemindCount(0);
        todo.setReportedInNext(false);
        todo.setCreatedAt(LocalDateTime.now());
        todoMapper.insert(todo);
        return todo;
    }

    /** 待办列表应返回会议下待办；不存在的会议应返回 404。 */
    @Test
    @DisplayName("GET /meetings/{id}/todos — 列表与会议校验")
    void listTodos() throws Exception {
        String mid = createMeetingId();
        insertTodo(mid, "user-a");

        mockMvc.perform(get("/api/v1/meetings/{id}/todos", mid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].content").value("跟进事项"));

        mockMvc.perform(get("/api/v1/meetings/{id}/todos", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    /** 待办看板应返回统计、标题与待办明细。 */
    @Test
    @DisplayName("GET /meetings/{id}/todo-board — 统计与标题")
    void todoBoard() throws Exception {
        String mid = createMeetingId();
        insertTodo(mid, "user-a");

        mockMvc.perform(get("/api/v1/meetings/{id}/todo-board", mid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.meetingId").value(mid))
                .andExpect(jsonPath("$.data.meetingTitle").value("待办 API 测试"))
                .andExpect(jsonPath("$.data.statusCounts.PENDING").value(1))
                .andExpect(jsonPath("$.data.todos.length()").value(1));
    }

    /** 更新待办状态为完成应同步参会人 completedCount；回退时计数归零。 */
    @Test
    @DisplayName("PUT /todos/{tid}/status — 完成态与参会人 completedCount")
    void updateStatus() throws Exception {
        String mid = createMeetingId();
        MeetingTodo todo = insertTodo(mid, "user-a");

        TodoStatusUpdateRequest body = new TodoStatusUpdateRequest();
        body.setStatus(TodoStatus.COMPLETED.name());
        body.setCompletionNote("已闭环");

        mockMvc.perform(put("/api/v1/todos/{tid}/status", todo.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.completionNote").value("已闭环"))
                .andExpect(jsonPath("$.data.completedAt").isNotEmpty());

        LambdaQueryWrapper<Participant> pw = new LambdaQueryWrapper<>();
        pw.eq(Participant::getMeetingId, mid).eq(Participant::getUserId, "user-a");
        Participant p = participantMapper.selectOne(pw);
        org.junit.jupiter.api.Assertions.assertNotNull(p);
        org.junit.jupiter.api.Assertions.assertEquals(1, p.getCompletedCount());

        body.setStatus(TodoStatus.IN_PROGRESS.name());
        mockMvc.perform(put("/api/v1/todos/{tid}/status", todo.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
        p = participantMapper.selectById(p.getId());
        org.junit.jupiter.api.Assertions.assertEquals(0, p.getCompletedCount());
    }

    /** 非法状态值应返回 400 业务码。 */
    @Test
    @DisplayName("PUT /todos/{tid}/status — 非法状态")
    void updateStatusInvalid() throws Exception {
        String mid = createMeetingId();
        MeetingTodo todo = insertTodo(mid, "user-a");

        TodoStatusUpdateRequest body = new TodoStatusUpdateRequest();
        body.setStatus("NOT_A_STATUS");

        mockMvc.perform(put("/api/v1/todos/{tid}/status", todo.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    /** 指派待办应更新 assigneeId 与 assigneeName。 */
    @Test
    @DisplayName("PUT /todos/{tid}/assign")
    void assign() throws Exception {
        String mid = createMeetingId();
        MeetingTodo todo = insertTodo(mid, "user-a");

        TodoAssignRequest body = new TodoAssignRequest();
        body.setAssigneeId("user-b");
        body.setAssigneeName("李四");

        mockMvc.perform(put("/api/v1/todos/{tid}/assign", todo.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assigneeId").value("user-b"))
                .andExpect(jsonPath("$.data.assigneeName").value("李四"));
    }

    /** 更新不存在的待办应返回 404。 */
    @Test
    @DisplayName("PUT /todos/{tid}/status — 待办不存在")
    void updateUnknownTodo() throws Exception {
        TodoStatusUpdateRequest body = new TodoStatusUpdateRequest();
        body.setStatus(TodoStatus.PENDING.name());

        mockMvc.perform(put("/api/v1/todos/{tid}/status", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }
}

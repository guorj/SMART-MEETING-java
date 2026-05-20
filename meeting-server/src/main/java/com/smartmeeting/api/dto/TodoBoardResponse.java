package com.smartmeeting.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 会议待办看板响应体（{@code GET /api/v1/meetings/{id}/todo-board}）。
 * <p>
 * 聚合各状态计数与完整待办列表，便于前端分栏展示。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TodoBoardResponse {
    private String meetingId;
    private String meetingTitle;
    /** 各 {@link com.smartmeeting.enums.TodoStatus} 数量 */
    private Map<String, Integer> statusCounts;
    /** 与 GET …/todos 相同排序的完整列表，便于前端自行分栏 */
    private List<MeetingTodoResponse> todos;
}

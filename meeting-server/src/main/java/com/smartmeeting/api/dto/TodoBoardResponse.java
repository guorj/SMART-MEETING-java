package com.smartmeeting.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

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

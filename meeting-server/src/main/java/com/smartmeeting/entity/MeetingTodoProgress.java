package com.smartmeeting.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 待办进度时间线，对应 {@code int_meeting_todo_progress}。
 */
@Data
@TableName("int_meeting_todo_progress")
public class MeetingTodoProgress {
    @TableId
    private String id;
    private String todoId;
    private String authorId;
    private String authorName;
    /** ASSIGNEE 或 OPERATOR */
    private String authorRole;
    private String progressText;
    private Integer progressPercent;
    private LocalDateTime createdAt;
}

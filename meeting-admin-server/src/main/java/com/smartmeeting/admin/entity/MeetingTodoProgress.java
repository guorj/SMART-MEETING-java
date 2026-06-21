package com.smartmeeting.admin.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("int_meeting_todo_progress")
public class MeetingTodoProgress {
    @TableId
    private String id;
    private String todoId;
    private String authorId;
    private String authorName;
    private String authorRole;
    private String progressText;
    private Integer progressPercent;
    private LocalDateTime createdAt;
}

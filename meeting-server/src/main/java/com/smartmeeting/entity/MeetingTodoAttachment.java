package com.smartmeeting.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 待办附件，对应 {@code int_meeting_todo_attachment}。
 */
@Data
@TableName("int_meeting_todo_attachment")
public class MeetingTodoAttachment {
    @TableId
    private String id;
    private String todoId;
    private String progressId;
    private String uploaderId;
    private String uploaderName;
    private String fileName;
    private String storagePath;
    private Long fileSize;
    private String mimeType;
    private LocalDateTime createdAt;
}

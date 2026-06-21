package com.smartmeeting.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("int_meeting_todo_audit")
public class MeetingTodoAudit {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String todoId;
    private String action;
    private String oldStatus;
    private String newStatus;
    private String operatorId;
    private String operatorName;
    private String reason;
    private String payloadJson;
    private LocalDateTime createdAt;
}

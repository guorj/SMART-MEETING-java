package com.smartmeeting.admin.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("int_scheduled_push_log_read_user")
public class PushLogReadUser {
    @TableId
    private String id;
    private String pushLogId;
    private String userIdType;
    private String userId;
    private LocalDateTime readAt;
    private String tenantKey;
    private String userName;
}

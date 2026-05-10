package com.smartmeeting.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("int_voiceprint")
public class Voiceprint {
    @TableId
    private String id;
    private Integer userId;
    private String userName;
    private String feishuUserId;
    private String featureId;
    private String groupId;
    private LocalDateTime registeredAt;
    private LocalDateTime expiresAt;
}

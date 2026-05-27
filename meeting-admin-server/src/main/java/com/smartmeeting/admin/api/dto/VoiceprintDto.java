package com.smartmeeting.admin.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class VoiceprintDto {
    private String id;
    private Integer userId;
    private String userName;
    private String feishuUserId;
    private String featureId;
    private String groupId;
    private LocalDateTime registeredAt;
    private LocalDateTime expiresAt;
    /** 计算字段：VALID / EXPIRED / EXPIRING（距过期不足 48h） */
    private String expiryStatus;
}

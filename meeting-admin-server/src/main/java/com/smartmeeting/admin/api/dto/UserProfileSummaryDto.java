package com.smartmeeting.admin.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户档案列表行：聚合 {@code int_user_mapping_feishu} + 主声纹（同 userId 最新一条）。
 */
@Data
public class UserProfileSummaryDto {
    private Integer userId;
    private String userName;
    private String feishuUserId;

    private boolean hasVoiceprint;
    private String voiceprintId;
    private String featureId;
    private String groupId;
    private LocalDateTime registeredAt;
    private LocalDateTime expiresAt;
    private String expiryStatus;
    private int voiceprintCount;
}

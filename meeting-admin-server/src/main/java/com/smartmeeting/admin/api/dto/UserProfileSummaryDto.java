package com.smartmeeting.admin.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户档案列表行：聚合 {@code int_user_mapping_feishu} + 主声纹（同 userId 最新一条）。
 * 后续新增用户相关表可在此 DTO 扩展字段。
 */
@Data
public class UserProfileSummaryDto {
    private Integer userId;
    private String userName;
    private String feishuUserId;
    private String feishuUnionId;
    private String feishuOpenId;

    private boolean hasVoiceprint;
    private String voiceprintId;
    private String featureId;
    private String groupId;
    private LocalDateTime registeredAt;
    private LocalDateTime expiresAt;
    private String expiryStatus;
    /** 同 userId 声纹条数（&gt;1 时列表 featureId 列显示提示） */
    private int voiceprintCount;
}

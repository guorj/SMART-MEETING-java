package com.smartmeeting.admin.api.dto;

import lombok.Data;

import java.util.List;

@Data
public class UserProfileDetailDto {
    private UserMappingDto mapping;
    /** 主声纹（registeredAt 最新）；无则为 null */
    private VoiceprintDto primaryVoiceprint;
    private List<VoiceprintDto> voiceprints;
    /** 前台授权；无 feishuUserId 或未在白名单时为 null */
    private UserDashboardGrantDto dashboardGrant;
}

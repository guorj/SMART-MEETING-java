package com.smartmeeting.admin.api.dto;

import lombok.Data;

import java.util.List;

@Data
public class UserProfileDetailDto {
    private UserMappingDto mapping;
    /** 主声纹（registeredAt 最新）；无则为 null */
    private VoiceprintDto primaryVoiceprint;
    private List<VoiceprintDto> voiceprints;
}

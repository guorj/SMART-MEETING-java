package com.smartmeeting.admin.api.dto;

import lombok.Data;

@Data
public class UserProfileSaveDto {
    private UserMappingDto mapping;
    /** 可选；featureId 为空则仅保存映射、不新建/改声纹 */
    private VoiceprintDto voiceprint;
    /** 为 true 时删除该用户全部声纹（保存映射后执行） */
    private boolean clearVoiceprint;
}

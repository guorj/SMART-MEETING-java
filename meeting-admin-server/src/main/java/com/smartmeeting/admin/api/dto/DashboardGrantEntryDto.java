package com.smartmeeting.admin.api.dto;

import lombok.Data;

/**
 * dashboard.user_grants 单条授权条目。
 */
@Data
public class DashboardGrantEntryDto {
    private String feishuUserId;
    private String userName;
    private boolean enabled = true;
    private boolean canCreateMeeting = false;
    private boolean canEndMeeting = false;
    private boolean canRegisterVoiceprint = true;
    private String remark;
}

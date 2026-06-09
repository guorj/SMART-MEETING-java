package com.smartmeeting.admin.api.dto;

import lombok.Data;

/**
 * 用户档案中的前台授权子集（feishuUserId 取自 mapping，不在此重复）。
 */
@Data
public class UserDashboardGrantDto {
    private boolean enabled;
    private boolean canCreateMeeting;
    private boolean canEndMeeting;
    private boolean canRegisterVoiceprint = true;
    private String remark;
}

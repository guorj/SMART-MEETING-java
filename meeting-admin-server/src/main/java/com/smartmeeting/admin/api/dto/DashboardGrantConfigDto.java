package com.smartmeeting.admin.api.dto;

import lombok.Data;

import java.util.List;

/**
 * dashboard.user_grants 完整配置。
 */
@Data
public class DashboardGrantConfigDto {
    private boolean defaultDeny = true;
    private List<DashboardGrantEntryDto> entries;
}

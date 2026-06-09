package com.smartmeeting.admin.api.dto;

import lombok.Data;

/**
 * 前台授权全局策略（defaultDeny）。
 */
@Data
public class DashboardGrantPolicyDto {
    private boolean defaultDeny = true;
}

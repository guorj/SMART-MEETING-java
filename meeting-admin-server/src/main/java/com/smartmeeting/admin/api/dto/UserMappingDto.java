package com.smartmeeting.admin.api.dto;

import lombok.Data;

@Data
public class UserMappingDto {
    private Integer userId;
    private String userName;
    private String feishuUserId;
}

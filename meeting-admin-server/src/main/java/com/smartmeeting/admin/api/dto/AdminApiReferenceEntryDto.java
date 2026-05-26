package com.smartmeeting.admin.api.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 管理端数据更新 API 说明条目（供 API 参考模块展示）。
 */
@Data
@Builder
public class AdminApiReferenceEntryDto {
    private String id;
    private String category;
    private String method;
    private String path;
    private String title;
    private String description;
    /** 如 admin-server、meeting-server（经桥接） */
    private String targetProcess;
    private List<String> authHeaders;
    private String requestContentType;
    private String requestExample;
    private String responseExample;
    private String curlExample;
    private List<String> notes;
}

package com.smartmeeting.admin.api.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgendaMaterialUploadDto {
    private String fileId;
    private String originalFilename;
    private String mimeType;
    private long sizeBytes;
    /** 管理端预览/下载相对路径 */
    private String previewUrl;
}

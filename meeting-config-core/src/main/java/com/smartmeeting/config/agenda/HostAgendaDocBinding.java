package com.smartmeeting.config.agenda;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会序项内嵌资料（host_agenda v2 {@code items[].docs[]}）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HostAgendaDocBinding {
    private String configName;
    private String role;
    private Integer slot;
    /** FEISHU（默认）或 LOCAL */
    private String storageKind;
    private String url;
    /** 本地上传资料 UUID，storageKind=LOCAL 时必填 */
    private String fileId;
    private String originalFilename;
    private String mimeType;
    private String bitableDisplayMode;
    private Boolean enabled;
    private String generatedReportUrl;
    private LocalDateTime generatedReportAt;

    public int resolvedSlot() {
        return slot != null && slot >= 0 ? slot : 0;
    }

    public boolean isEnabled() {
        return enabled == null || enabled;
    }

    /** 是否为本地上传资料（非飞书 URL）。 */
    public boolean isLocalStorage() {
        return AgendaStorageKind.LOCAL.equalsIgnoreCase(storageKind);
    }
}

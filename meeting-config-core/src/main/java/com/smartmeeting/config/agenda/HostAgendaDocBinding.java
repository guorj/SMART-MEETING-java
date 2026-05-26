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
    private String url;
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
}

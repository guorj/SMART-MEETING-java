package com.smartmeeting.config.agenda;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgendaDocBindingSnapshot {
    private Long id;
    private String configName;
    private Integer presetTypeCode;
    private Integer agendaIndex;
    private Integer resourceSlot;
    private String feishuDocUrl;
    private Integer enabled;
    private String configRole;
    private String bitableDisplayMode;
    private String generatedReportUrl;
    private LocalDateTime generatedReportAt;
}

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
    /** FEISHU（默认）或 LOCAL */
    private String storageKind;
    private String fileId;
    private String originalFilename;
    private String mimeType;
    private Integer enabled;
    /** 1=主持页展示，0=隐藏 */
    private Integer showInHost;
    private String configRole;
    private String bitableDisplayMode;

    public boolean isShowInHost() {
        return showInHost == null || showInHost == 1;
    }
}

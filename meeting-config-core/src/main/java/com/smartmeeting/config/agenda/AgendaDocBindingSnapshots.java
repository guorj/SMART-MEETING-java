package com.smartmeeting.config.agenda;

import java.util.ArrayList;
import java.util.List;

/**
 * 应用层将持久化实体转为 {@link AgendaDocBindingSnapshot} 的辅助类（core 不依赖 MyBatis 实体）。
 */
public final class AgendaDocBindingSnapshots {

    private AgendaDocBindingSnapshots() {
    }

    public static AgendaDocBindingSnapshot of(Long id, String configName, Integer presetTypeCode,
                                              Integer agendaIndex, Integer resourceSlot,
                                              String feishuDocUrl, Integer enabled, String configRole,
                                              String bitableDisplayMode, String generatedReportUrl,
                                              java.time.LocalDateTime generatedReportAt) {
        return AgendaDocBindingSnapshot.builder()
                .id(id)
                .configName(configName)
                .presetTypeCode(presetTypeCode)
                .agendaIndex(agendaIndex)
                .resourceSlot(resourceSlot)
                .feishuDocUrl(feishuDocUrl)
                .enabled(enabled)
                .configRole(configRole)
                .bitableDisplayMode(bitableDisplayMode)
                .generatedReportUrl(generatedReportUrl)
                .generatedReportAt(generatedReportAt)
                .build();
    }

    public static List<AgendaDocBindingSnapshot> filterEnabledWithAgendaIndex(
            List<AgendaDocBindingSnapshot> bindings) {
        if (bindings == null) {
            return List.of();
        }
        List<AgendaDocBindingSnapshot> out = new ArrayList<>();
        for (AgendaDocBindingSnapshot b : bindings) {
            if (b.getEnabled() != null && b.getEnabled() == 1
                    && b.getAgendaIndex() != null && b.getAgendaIndex() >= 0) {
                out.add(b);
            }
        }
        return out;
    }
}

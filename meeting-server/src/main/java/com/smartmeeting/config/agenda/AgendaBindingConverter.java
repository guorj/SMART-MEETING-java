package com.smartmeeting.config.agenda;

import com.smartmeeting.entity.MatterProgressDocConfig;

import java.util.ArrayList;
import java.util.List;

/** meeting-server 实体 → meeting-config-core 快照。 */
public final class AgendaBindingConverter {

    private AgendaBindingConverter() {
    }

    public static AgendaDocBindingSnapshot from(MatterProgressDocConfig c) {
        if (c == null) {
            return null;
        }
        return AgendaDocBindingSnapshots.of(
                c.getId(),
                c.getConfigName(),
                c.getPresetTypeCode(),
                c.getAgendaIndex(),
                c.getResourceSlot(),
                c.getFeishuDocUrl(),
                c.getEnabled(),
                c.getConfigRole(),
                c.getBitableDisplayMode(),
                c.getGeneratedReportUrl(),
                c.getGeneratedReportAt());
    }

    public static List<AgendaDocBindingSnapshot> fromList(List<MatterProgressDocConfig> list) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        List<AgendaDocBindingSnapshot> out = new ArrayList<>(list.size());
        for (MatterProgressDocConfig c : list) {
            out.add(from(c));
        }
        return out;
    }
}

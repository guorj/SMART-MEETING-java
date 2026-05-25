package com.smartmeeting.config.agenda;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgendaPresetSnapshot {
    private int presetTypeCode;
    private String displayName;
    private String company;
    private String department;
    private String groupName;
    private String scheduleNote;
    private String agendaSummary;
    private String organizerName;
    private String leaderName;
    private String participantsNames;
    /** JSON: {"items":[{"title","minutes"},...]} */
    private String hostAgendaJson;
    private List<AgendaDocBindingSnapshot> docBindings;
}

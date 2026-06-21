package com.smartmeeting.config.agenda;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
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
    /** JSON: PresetScheduleConfig */
    @JsonDeserialize(using = ScheduleConfigJsonDeserializer.class)
    private String scheduleConfig;
    private String agendaSummary;
    private String organizerName;
    private String leaderName;
    private String participantsNames;
    /** JSON: {"items":[{"title","minutes"},...]} */
    private String hostAgendaJson;
    private List<AgendaDocBindingSnapshot> docBindings;
}

package com.smartmeeting.admin.agenda;

import com.smartmeeting.admin.entity.MeetingTypePreset;
import com.smartmeeting.admin.repository.MeetingTypePresetMapper;
import com.smartmeeting.config.agenda.AgendaConfigProvider;
import com.smartmeeting.config.agenda.AgendaPresetSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PresetTableAgendaConfigProvider implements AgendaConfigProvider {

    private final MeetingTypePresetMapper presetMapper;

    @Override
    public String providerId() {
        return "preset_table";
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public Optional<AgendaPresetSnapshot> loadPreset(int presetTypeCode) {
        if (presetTypeCode < 1 || presetTypeCode > 5) {
            return Optional.empty();
        }
        MeetingTypePreset p = presetMapper.selectById(presetTypeCode);
        if (p == null) {
            return Optional.empty();
        }
        return Optional.of(AgendaPresetSnapshot.builder()
                .presetTypeCode(p.getCode())
                .displayName(p.getDisplayName())
                .company(p.getCompany())
                .department(p.getDepartment())
                .groupName(p.getGroupName())
                .scheduleNote(p.getScheduleNote())
                .agendaSummary(p.getAgendaSummary())
                .organizerName(p.getOrganizerName())
                .leaderName(p.getLeaderName())
                .participantsNames(p.getParticipantsNames())
                .hostAgendaJson(p.getHostAgenda())
                .build());
    }

    @Override
    public void savePreset(AgendaPresetSnapshot snapshot) {
        if (snapshot == null || snapshot.getPresetTypeCode() < 1 || snapshot.getPresetTypeCode() > 5) {
            throw new IllegalArgumentException("invalid presetTypeCode");
        }
        MeetingTypePreset row = presetMapper.selectById(snapshot.getPresetTypeCode());
        if (row == null) {
            row = new MeetingTypePreset();
            row.setCode(snapshot.getPresetTypeCode());
        }
        row.setDisplayName(snapshot.getDisplayName());
        row.setCompany(snapshot.getCompany());
        row.setDepartment(snapshot.getDepartment());
        row.setGroupName(snapshot.getGroupName());
        row.setScheduleNote(snapshot.getScheduleNote());
        row.setAgendaSummary(snapshot.getAgendaSummary());
        row.setOrganizerName(snapshot.getOrganizerName());
        row.setLeaderName(snapshot.getLeaderName());
        row.setParticipantsNames(snapshot.getParticipantsNames());
        row.setHostAgenda(snapshot.getHostAgendaJson());
        if (presetMapper.selectById(row.getCode()) == null) {
            presetMapper.insert(row);
        } else {
            presetMapper.updateById(row);
        }
    }
}

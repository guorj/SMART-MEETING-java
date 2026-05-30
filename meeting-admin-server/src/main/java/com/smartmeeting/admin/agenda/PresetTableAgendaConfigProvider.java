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
        if (presetTypeCode <= 0) {
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
        if (snapshot == null || snapshot.getPresetTypeCode() <= 0) {
            throw new IllegalArgumentException("invalid presetTypeCode");
        }
        if (snapshot.getPresetTypeCode() > 127) {
            throw new IllegalArgumentException("presetTypeCode exceeds tinyint max(127)");
        }
        MeetingTypePreset row = presetMapper.selectById(snapshot.getPresetTypeCode());
        if (row == null) {
            row = new MeetingTypePreset();
            row.setCode(snapshot.getPresetTypeCode());
        }
        row.setDisplayName(req(snapshot.getDisplayName(), "会务类型" + snapshot.getPresetTypeCode()));
        row.setCompany(req(snapshot.getCompany(), "未设置集团"));
        row.setDepartment(opt(snapshot.getDepartment()));
        row.setGroupName(req(snapshot.getGroupName(), "未设置会议组"));
        row.setScheduleNote(opt(snapshot.getScheduleNote()));
        row.setAgendaSummary(opt(snapshot.getAgendaSummary()));
        row.setOrganizerName(opt(snapshot.getOrganizerName()));
        row.setLeaderName(opt(snapshot.getLeaderName()));
        row.setParticipantsNames(opt(snapshot.getParticipantsNames()));
        row.setHostAgenda(snapshot.getHostAgendaJson());
        if (presetMapper.selectById(row.getCode()) == null) {
            presetMapper.insert(row);
        } else {
            presetMapper.updateById(row);
        }
    }

    private static String req(String value, String fallback) {
        String v = value != null ? value.trim() : "";
        if (v.isEmpty()) {
            return fallback;
        }
        return v;
    }

    private static String opt(String value) {
        return value == null ? "" : value.trim();
    }
}

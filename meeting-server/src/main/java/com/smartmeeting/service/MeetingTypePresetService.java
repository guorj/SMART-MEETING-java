package com.smartmeeting.service;

import com.smartmeeting.api.dto.MeetingCreateRequest;
import com.smartmeeting.api.dto.MeetingPresetResponse;
import com.smartmeeting.entity.MeetingTypePreset;
import com.smartmeeting.exception.BusinessException;
import com.smartmeeting.repository.MeetingTypePresetMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MeetingTypePresetService {

    public static final String DEFAULT_COMPANY = "吉青汽车科技集团";
    public static final String OTHER_GROUP = "其他会议";

    private final MeetingTypePresetMapper presetMapper;

    public List<MeetingPresetResponse> listPresets() {
        return presetMapper.selectList(null).stream()
                .sorted((a, b) -> Integer.compare(a.getCode(), b.getCode()))
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public MeetingPresetResponse toResponse(MeetingTypePreset p) {
        return MeetingPresetResponse.builder()
                .code(p.getCode())
                .displayName(p.getDisplayName())
                .company(p.getCompany())
                .department(p.getDepartment())
                .groupName(p.getGroupName())
                .scheduleNote(p.getScheduleNote())
                .agendaSummary(p.getAgendaSummary())
                .organizerName(p.getOrganizerName())
                .leaderName(p.getLeaderName())
                .participantNames(splitNames(p.getParticipantsNames()))
                .build();
    }

    /**
     * 按 {@link MeetingCreateRequest#getPresetTypeCode()} 合并预设到请求（会修改 request）。
     * 1-5：自库加载；6：仅补全默认集团/会议组（主题须已有）。
     */
    public void mergeIntoCreateRequest(MeetingCreateRequest request) {
        Integer code = request.getPresetTypeCode();
        if (code == null) {
            return;
        }
        if (code == 6) {
            if (request.getCompany() == null || request.getCompany().isBlank()) {
                request.setCompany(DEFAULT_COMPANY);
            }
            if (request.getGroupName() == null || request.getGroupName().isBlank()) {
                request.setGroupName(OTHER_GROUP);
            }
            return;
        }
        if (code < 1 || code > 5) {
            throw new BusinessException(400, "presetTypeCode 仅支持 1-6");
        }
        MeetingTypePreset p = presetMapper.selectById(code);
        if (p == null) {
            throw new BusinessException(400, "未找到会议类型预设: " + code);
        }
        request.setTitle(p.getDisplayName());
        request.setCompany(p.getCompany());
        request.setDepartment(p.getDepartment());
        request.setGroupName(p.getGroupName());
        List<String> agenda = new ArrayList<>();
        if (p.getScheduleNote() != null && !p.getScheduleNote().isBlank()) {
            agenda.add("召开时间：" + p.getScheduleNote());
        }
        if (p.getAgendaSummary() != null && !p.getAgendaSummary().isBlank()) {
            agenda.add("会议内容：" + p.getAgendaSummary());
        }
        if (p.getOrganizerName() != null && !p.getOrganizerName().isBlank()) {
            agenda.add("组织人：" + p.getOrganizerName());
        }
        if (p.getLeaderName() != null && !p.getLeaderName().isBlank()) {
            agenda.add("会议主导：" + p.getLeaderName());
        }
        request.setAgenda(agenda);

        List<MeetingCreateRequest.ParticipantEntry> entries = new ArrayList<>();
        for (String name : splitNames(p.getParticipantsNames())) {
            if (name.isEmpty()) {
                continue;
            }
            MeetingCreateRequest.ParticipantEntry e = new MeetingCreateRequest.ParticipantEntry();
            e.setName(name);
            e.setUserId("vp_" + UUID.randomUUID().toString().replace("-", ""));
            entries.add(e);
        }
        request.setParticipants(entries);
    }

    public List<String> splitNames(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String s = raw.replace("以及", ",").replace("及指定相关人员", "").trim();
        return Arrays.stream(s.split("[、,，;；\\s]+"))
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .collect(Collectors.toList());
    }
}

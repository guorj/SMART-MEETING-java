package com.smartmeeting.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.admin.api.dto.MeetingAdminDetailDto;
import com.smartmeeting.admin.api.dto.MeetingAdminLinksDto;
import com.smartmeeting.admin.api.dto.MeetingAdminSummaryDto;
import com.smartmeeting.admin.api.dto.ParticipantSummaryDto;
import com.smartmeeting.admin.config.RuntimeBridgeProperties;
import com.smartmeeting.admin.entity.Meeting;
import com.smartmeeting.admin.entity.MeetingParticipant;
import com.smartmeeting.admin.exception.BusinessException;
import com.smartmeeting.admin.repository.MeetingMapper;
import com.smartmeeting.admin.repository.MeetingParticipantMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingAdminService {

    private final MeetingMapper meetingMapper;
    private final MeetingParticipantMapper participantMapper;
    private final RuntimeBridgeProperties bridgeProperties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    public Page<MeetingAdminSummaryDto> list(int page, int size, String status, Integer presetTypeCode) {
        LambdaQueryWrapper<Meeting> q = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) {
            q.eq(Meeting::getStatus, status);
        }
        if (presetTypeCode != null) {
            q.eq(Meeting::getPresetTypeCode, presetTypeCode);
        }
        q.orderByDesc(Meeting::getCreatedAt);
        Page<Meeting> raw = meetingMapper.selectPage(new Page<>(page, size), q);
        Page<MeetingAdminSummaryDto> out = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        out.setRecords(raw.getRecords().stream().map(this::toSummary).toList());
        return out;
    }

    public MeetingAdminDetailDto detail(String meetingId) {
        Meeting m = meetingMapper.selectById(meetingId);
        if (m == null) {
            throw new BusinessException("meeting not found");
        }
        MeetingAdminDetailDto dto = new MeetingAdminDetailDto();
        dto.setSummary(toSummary(m));
        dto.setHostAgendaJson(m.getHostAgenda());
        dto.setLinks(buildLinks(meetingId));
        dto.setParticipants(listParticipants(meetingId));
        return dto;
    }

    public List<ParticipantSummaryDto> listParticipants(String meetingId) {
        LambdaQueryWrapper<MeetingParticipant> q = new LambdaQueryWrapper<>();
        q.eq(MeetingParticipant::getMeetingId, meetingId).orderByAsc(MeetingParticipant::getName);
        return participantMapper.selectList(q).stream()
                .map(p -> ParticipantSummaryDto.builder()
                        .id(p.getId())
                        .name(p.getName())
                        .status(p.getStatus())
                        .attendanceMode(p.getAttendanceMode())
                        .checkedInAt(p.getCheckedInAt())
                        .checkInSource(p.getCheckInSource())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 调用 meeting-server 通用结束接口（无 JWT）。
     */
    public boolean forceEndMeeting(String meetingId) {
        String url = bridgeProperties.getMeetingServerBaseUrl().replaceAll("/$", "")
                + "/api/v1/meetings/" + meetingId + "/end";
        try {
            ResponseEntity<String> resp = restTemplate.postForEntity(url, null, String.class);
            if (!resp.getStatusCode().is2xxSuccessful()) {
                return false;
            }
            JsonNode root = objectMapper.readTree(resp.getBody());
            return root.path("code").asInt(-1) == 0;
        } catch (Exception e) {
            log.warn("forceEndMeeting {} failed: {}", meetingId, e.getMessage());
            throw new BusinessException("结束会议失败: " + e.getMessage());
        }
    }

    public MeetingAdminLinksDto buildLinks(String meetingId) {
        String base = bridgeProperties.getMeetingServerBaseUrl().replaceAll("/$", "");
        MeetingAdminLinksDto links = new MeetingAdminLinksDto();
        links.setHostUrl(base + "/host/" + meetingId);
        links.setRecorderUrl(base + "/rec/" + meetingId);
        return links;
    }

    private MeetingAdminSummaryDto toSummary(Meeting m) {
        return MeetingAdminSummaryDto.builder()
                .id(m.getId())
                .title(m.getTitle())
                .status(m.getStatus())
                .presetTypeCode(m.getPresetTypeCode())
                .scheduledTime(m.getScheduledTime())
                .actualStartTime(m.getActualStartTime())
                .actualEndTime(m.getActualEndTime())
                .createdAt(m.getCreatedAt())
                .build();
    }
}

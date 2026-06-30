package com.smartmeeting.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.admin.api.dto.MeetingAdminDetailDto;
import com.smartmeeting.admin.api.dto.MeetingAdminFullDto;
import com.smartmeeting.admin.api.dto.MeetingAdminLinksDto;
import com.smartmeeting.admin.api.dto.MeetingAdminScheduleRequest;
import com.smartmeeting.admin.api.dto.MeetingAdminSummaryDto;
import com.smartmeeting.admin.api.dto.MeetingAdminUpdateRequest;
import com.smartmeeting.admin.api.dto.MeetingBatchDeleteResultDto;
import com.smartmeeting.admin.api.dto.ParticipantAdminDetailDto;
import com.smartmeeting.admin.config.RuntimeBridgeProperties;
import com.smartmeeting.admin.entity.Meeting;
import com.smartmeeting.admin.entity.MeetingParticipant;
import com.smartmeeting.admin.exception.BusinessException;
import com.smartmeeting.admin.repository.MeetingMapper;
import com.smartmeeting.admin.repository.MeetingParticipantMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingAdminService {

    private static final int BATCH_DELETE_MAX = 50;
    private static final Set<String> NON_DELETABLE_STATUSES = Set.of(
            "STARTED", "RECORDING", "PROCESSING", "PAUSED");

    private final MeetingMapper meetingMapper;
    private final MeetingParticipantMapper participantMapper;
    private final RuntimeBridgeProperties bridgeProperties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    public Page<MeetingAdminSummaryDto> list(int page, int size, String status, Integer presetTypeCode,
                                             String creatorId, String chatId, String company,
                                             LocalDateTime scheduledTimeFrom, LocalDateTime scheduledTimeTo) {
        LambdaQueryWrapper<Meeting> q = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) {
            q.eq(Meeting::getStatus, status);
        }
        if (presetTypeCode != null) {
            q.eq(Meeting::getPresetTypeCode, presetTypeCode);
        }
        if (StringUtils.hasText(creatorId)) {
            q.eq(Meeting::getCreatorId, creatorId);
        }
        if (StringUtils.hasText(chatId)) {
            q.eq(Meeting::getChatId, chatId);
        }
        if (StringUtils.hasText(company)) {
            q.eq(Meeting::getCompany, company);
        }
        if (scheduledTimeFrom != null) {
            q.ge(Meeting::getScheduledTime, scheduledTimeFrom);
        }
        if (scheduledTimeTo != null) {
            q.le(Meeting::getScheduledTime, scheduledTimeTo);
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
        dto.setMeeting(toFull(m));
        dto.setHostAgendaJson(m.getHostAgenda());
        dto.setLinks(buildLinks(meetingId));
        dto.setParticipants(listParticipants(meetingId));
        return dto;
    }

    public List<ParticipantAdminDetailDto> listParticipants(String meetingId) {
        LambdaQueryWrapper<MeetingParticipant> q = new LambdaQueryWrapper<>();
        q.eq(MeetingParticipant::getMeetingId, meetingId).orderByAsc(MeetingParticipant::getName);
        return participantMapper.selectList(q).stream().map(this::toParticipantDetail).collect(Collectors.toList());
    }

    public JsonNode updateMeeting(String meetingId, MeetingAdminUpdateRequest request) {
        return bridgeJson(HttpMethod.PUT, "/api/v1/internal/meetings/" + meetingId, request);
    }

    public JsonNode rescheduleMeeting(String meetingId, MeetingAdminScheduleRequest request) {
        return bridgeJson(HttpMethod.PATCH, "/api/v1/internal/meetings/" + meetingId + "/schedule", request);
    }

    public JsonNode addParticipant(String meetingId, Map<String, Object> body) {
        return bridgeJson(HttpMethod.POST, "/api/v1/internal/meetings/" + meetingId + "/participants", body);
    }

    public JsonNode updateParticipant(String meetingId, String participantId, Map<String, Object> body) {
        return bridgeJson(HttpMethod.PUT,
                "/api/v1/internal/meetings/" + meetingId + "/participants/" + participantId, body);
    }

    public void deleteParticipant(String meetingId, String participantId) {
        bridgeJson(HttpMethod.DELETE,
                "/api/v1/internal/meetings/" + meetingId + "/participants/" + participantId, null);
    }

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

    public MeetingBatchDeleteResultDto batchDeleteMeetings(List<String> meetingIds) {
        if (meetingIds == null || meetingIds.isEmpty()) {
            throw new BusinessException("meetingIds 不能为空");
        }
        if (meetingIds.size() > BATCH_DELETE_MAX) {
            throw new BusinessException("单次最多删除 " + BATCH_DELETE_MAX + " 场会议");
        }
        int deleted = 0;
        List<MeetingBatchDeleteResultDto.SkippedMeeting> skipped = new ArrayList<>();
        for (String rawId : meetingIds) {
            String meetingId = rawId != null ? rawId.trim() : "";
            if (meetingId.isEmpty()) {
                continue;
            }
            Meeting m = meetingMapper.selectById(meetingId);
            if (m == null) {
                skipped.add(MeetingBatchDeleteResultDto.SkippedMeeting.builder()
                        .id(meetingId)
                        .reason("会议不存在")
                        .build());
                continue;
            }
            if (m.getStatus() != null && NON_DELETABLE_STATUSES.contains(m.getStatus())) {
                skipped.add(MeetingBatchDeleteResultDto.SkippedMeeting.builder()
                        .id(meetingId)
                        .reason("进行中会议须先结束（当前状态: " + m.getStatus() + "）")
                        .build());
                continue;
            }
            meetingMapper.deleteById(meetingId);
            deleted++;
        }
        return MeetingBatchDeleteResultDto.builder()
                .deleted(deleted)
                .skipped(skipped)
                .build();
    }

    public MeetingAdminLinksDto buildLinks(String meetingId) {
        String base = bridgeProperties.getMeetingServerBaseUrl().replaceAll("/$", "");
        MeetingAdminLinksDto links = new MeetingAdminLinksDto();
        links.setHostUrl(base + "/host/" + meetingId);
        links.setRecorderUrl(base + "/rec/" + meetingId);
        return links;
    }

    private JsonNode bridgeJson(HttpMethod method, String path, Object body) {
        String url = bridgeProperties.getMeetingServerBaseUrl().replaceAll("/$", "") + path;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Object> entity = body == null ? new HttpEntity<>(headers) : new HttpEntity<>(body, headers);
        try {
            ResponseEntity<String> resp = restTemplate.exchange(url, method, entity, String.class);
            JsonNode root = objectMapper.readTree(resp.getBody());
            if (root.path("code").asInt(-1) != 0) {
                throw new BusinessException(root.path("message").asText("bridge failed"));
            }
            return root.path("data");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("bridge {} {} failed: {}", method, path, e.getMessage());
            throw new BusinessException("调用 meeting-server 失败: " + e.getMessage());
        }
    }

    private MeetingAdminSummaryDto toSummary(Meeting m) {
        return MeetingAdminSummaryDto.builder()
                .id(m.getId())
                .title(m.getTitle())
                .status(m.getStatus())
                .presetTypeCode(m.getPresetTypeCode())
                .company(m.getCompany())
                .creatorId(m.getCreatorId())
                .chatId(m.getChatId())
                .scheduledTime(m.getScheduledTime())
                .actualStartTime(m.getActualStartTime())
                .actualEndTime(m.getActualEndTime())
                .createdAt(m.getCreatedAt())
                .updatedAt(m.getUpdatedAt())
                .build();
    }

    private MeetingAdminFullDto toFull(Meeting m) {
        return MeetingAdminFullDto.builder()
                .id(m.getId())
                .title(m.getTitle())
                .agendaJson(m.getAgenda())
                .hostAgendaJson(m.getHostAgenda())
                .company(m.getCompany())
                .department(m.getDepartment())
                .groupName(m.getGroupName())
                .presetTypeCode(m.getPresetTypeCode())
                .status(m.getStatus())
                .creatorId(m.getCreatorId())
                .chatId(m.getChatId())
                .roomId(m.getRoomId())
                .meetingScenario(m.getMeetingScenario())
                .sourceAudioUrl(m.getSourceAudioUrl())
                .previousMeetingId(m.getPreviousMeetingId())
                .scheduledTime(m.getScheduledTime())
                .actualStartTime(m.getActualStartTime())
                .actualEndTime(m.getActualEndTime())
                .durationSeconds(m.getDurationSeconds())
                .audioPath(m.getAudioPath())
                .docUrl(m.getDocUrl())
                .docToken(m.getDocToken())
                .recordingUrl(m.getRecordingUrl())
                .recordingToken(m.getRecordingToken())
                .vcMeetingUrl(m.getVcMeetingUrl())
                .vcMinuteToken(m.getVcMinuteToken())
                .vcRecordingUrl(m.getVcRecordingUrl())
                .createdAt(m.getCreatedAt())
                .updatedAt(m.getUpdatedAt())
                .build();
    }

    private ParticipantAdminDetailDto toParticipantDetail(MeetingParticipant p) {
        return ParticipantAdminDetailDto.builder()
                .id(p.getId())
                .meetingId(p.getMeetingId())
                .presetTypeCode(p.getPresetTypeCode())
                .userId(p.getUserId())
                .name(p.getName())
                .status(p.getStatus())
                .attendanceMode(p.getAttendanceMode())
                .featureId(p.getFeatureId())
                .voiceprintReady(p.getVoiceprintReady())
                .checkedInAt(p.getCheckedInAt())
                .checkInSource(p.getCheckInSource())
                .todoCount(p.getTodoCount())
                .completedCount(p.getCompletedCount())
                .build();
    }
}

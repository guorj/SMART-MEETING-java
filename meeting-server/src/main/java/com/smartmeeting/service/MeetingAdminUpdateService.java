package com.smartmeeting.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.api.dto.MeetingResponse;
import com.smartmeeting.api.dto.MeetingUpdateRequest;
import com.smartmeeting.api.dto.ParticipantAdminCreateRequest;
import com.smartmeeting.api.dto.ParticipantAdminUpdateRequest;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.entity.Participant;
import com.smartmeeting.enums.MeetingStatus;
import com.smartmeeting.exception.BusinessException;
import com.smartmeeting.repository.MeetingMapper;
import com.smartmeeting.repository.ParticipantMapper;
import com.smartmeeting.statemachine.MeetingStateMachineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingAdminUpdateService {

    private static final Set<String> NOT_STARTED = Set.of(
            MeetingStatus.ISSUE_COLLECTING.name(),
            MeetingStatus.INVITED.name());

    private final MeetingMapper meetingMapper;
    private final ParticipantMapper participantMapper;
    private final MeetingService meetingService;
    private final MeetingStateMachineService meetingStateMachineService;
    private final MeetingCalendarSyncService meetingCalendarSyncService;
    private final PostMeetingOrchestrator postMeetingOrchestrator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public MeetingResponse updateMeeting(String meetingId, MeetingUpdateRequest request) {
        if (request == null) {
            throw new BusinessException(400, "请求体不能为空");
        }
        Meeting meeting = requireMeeting(meetingId);
        boolean notStarted = meeting.getStatus() != null && NOT_STARTED.contains(meeting.getStatus());

        if (request.getTitle() != null) {
            meeting.setTitle(request.getTitle().trim());
        }
        if (notStarted) {
            if (request.getCompany() != null) {
                meeting.setCompany(request.getCompany());
            }
            if (request.getDepartment() != null) {
                meeting.setDepartment(request.getDepartment());
            }
            if (request.getGroupName() != null) {
                meeting.setGroupName(request.getGroupName());
            }
            if (request.getCreatorId() != null) {
                meeting.setCreatorId(request.getCreatorId());
            }
            if (request.getChatId() != null) {
                meeting.setChatId(request.getChatId());
            }
            if (request.getMeetingScenario() != null) {
                meeting.setMeetingScenario(request.getMeetingScenario().trim().toUpperCase());
            }
            if (request.getPreviousMeetingId() != null) {
                meeting.setPreviousMeetingId(request.getPreviousMeetingId());
            }
        }
        if (request.getAgenda() != null) {
            meeting.setAgenda(toAgendaJson(request.getAgenda()));
        }
        if (request.getHostAgendaJson() != null) {
            validateJsonObject(request.getHostAgendaJson(), "hostAgendaJson");
            meeting.setHostAgenda(request.getHostAgendaJson());
        }
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            applyStatusChange(meeting, request.getStatus().trim().toUpperCase());
        }
        if (request.getSourceAudioUrl() != null) {
            meeting.setSourceAudioUrl(request.getSourceAudioUrl());
        }
        if (request.getAudioPath() != null) {
            meeting.setAudioPath(request.getAudioPath());
        }
        if (request.getDocUrl() != null) {
            meeting.setDocUrl(request.getDocUrl());
        }
        if (request.getDocToken() != null) {
            meeting.setDocToken(request.getDocToken());
        }
        boolean vcTokenUpdated = false;
        if (request.getVcMinuteToken() != null) {
            String token = request.getVcMinuteToken().trim();
            meeting.setVcMinuteToken(token.isBlank() ? null : token);
            vcTokenUpdated = !token.isBlank();
        }

        meetingMapper.updateById(meeting);
        log.info("Meeting updated via admin: meetingId={}", meetingId);
        if (vcTokenUpdated) {
            postMeetingOrchestrator.resumePostMeetingAfterVcReady(meetingId);
        }
        return meetingService.getMeeting(meetingId);
    }

    @Transactional
    public MeetingResponse.ParticipantDTO addParticipant(String meetingId, ParticipantAdminCreateRequest request) {
        Meeting meeting = requireMeeting(meetingId);
        requireNotStarted(meeting);
        if (request == null || request.getName() == null || request.getName().isBlank()) {
            throw new BusinessException(400, "参会人姓名不能为空");
        }
        Participant p = new Participant();
        p.setId(UUID.randomUUID().toString());
        p.setMeetingId(meetingId);
        p.setPresetTypeCode(meeting.getPresetTypeCode());
        p.setUserId(request.getUserId());
        p.setName(request.getName().trim());
        p.setStatus("PENDING");
        p.setAttendanceMode(normalizeAttendance(request.getAttendanceMode()));
        p.setTodoCount(0);
        p.setCompletedCount(0);
        p.setVoiceprintReady(false);
        participantMapper.insert(p);
        return toParticipantDto(p);
    }

    @Transactional
    public MeetingResponse.ParticipantDTO updateParticipant(String meetingId, String participantId,
                                                            ParticipantAdminUpdateRequest request) {
        Meeting meeting = requireMeeting(meetingId);
        requireNotStarted(meeting);
        Participant p = requireParticipant(meetingId, participantId);
        if (request == null) {
            return toParticipantDto(p);
        }
        if (request.getUserId() != null) {
            p.setUserId(request.getUserId());
        }
        if (request.getName() != null && !request.getName().isBlank()) {
            p.setName(request.getName().trim());
        }
        if (request.getAttendanceMode() != null) {
            p.setAttendanceMode(normalizeAttendance(request.getAttendanceMode()));
        }
        participantMapper.updateById(p);
        return toParticipantDto(p);
    }

    @Transactional
    public void deleteParticipant(String meetingId, String participantId) {
        Meeting meeting = requireMeeting(meetingId);
        requireNotStarted(meeting);
        requireParticipant(meetingId, participantId);
        participantMapper.deleteById(participantId);
    }

    private void applyStatusChange(Meeting meeting, String targetStatus) {
        if (MeetingStatus.CANCELLED.name().equals(targetStatus)) {
            if (meeting.getStatus() == null || !NOT_STARTED.contains(meeting.getStatus())) {
                throw new BusinessException(400, "仅未开始会议可取消");
            }
            meetingCalendarSyncService.deleteScheduledCalendarEvent(meeting);
            meetingStateMachineService.forceStatus(meeting.getId(), MeetingStatus.CANCELLED);
            meeting.setStatus(MeetingStatus.CANCELLED.name());
            return;
        }
        throw new BusinessException(400, "不支持的状态变更: " + targetStatus);
    }

    private Meeting requireMeeting(String meetingId) {
        Meeting meeting = meetingMapper.selectById(meetingId);
        if (meeting == null) {
            throw new BusinessException(404, "会议不存在: " + meetingId);
        }
        return meeting;
    }

    private Participant requireParticipant(String meetingId, String participantId) {
        Participant p = participantMapper.selectById(participantId);
        if (p == null || !meetingId.equals(p.getMeetingId())) {
            throw new BusinessException(404, "参会人不存在");
        }
        return p;
    }

    private void requireNotStarted(Meeting meeting) {
        if (meeting.getStatus() == null || !NOT_STARTED.contains(meeting.getStatus())) {
            throw new BusinessException(400, "已开始会议不可修改参会人");
        }
    }

    private String normalizeAttendance(String mode) {
        if (mode == null || mode.isBlank()) {
            return "OFFLINE";
        }
        return mode.trim().toUpperCase();
    }

    private String toAgendaJson(List<String> agenda) {
        try {
            return objectMapper.writeValueAsString(agenda);
        } catch (Exception e) {
            throw new BusinessException(400, "agenda JSON 无效");
        }
    }

    private void validateJsonObject(String json, String field) {
        try {
            objectMapper.readValue(json, new TypeReference<Object>() {});
        } catch (Exception e) {
            throw new BusinessException(400, field + " 不是合法 JSON");
        }
    }

    private MeetingResponse.ParticipantDTO toParticipantDto(Participant p) {
        MeetingResponse.ParticipantDTO dto = new MeetingResponse.ParticipantDTO();
        dto.setUserId(p.getUserId());
        dto.setName(p.getName());
        dto.setStatus(p.getStatus());
        dto.setAttendanceMode(p.getAttendanceMode());
        dto.setCheckedInAt(p.getCheckedInAt());
        dto.setCheckInSource(p.getCheckInSource());
        return dto;
    }
}

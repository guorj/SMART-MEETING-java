package com.smartmeeting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.api.dto.MeetingCreateRequest;
import com.smartmeeting.api.dto.MeetingResponse;
import com.smartmeeting.api.dto.PreviousProgressResponse;
import com.smartmeeting.api.dto.PreviousProgressResponse.DelayedItem;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.entity.Participant;
import com.smartmeeting.enums.MeetingStatus;
import com.smartmeeting.exception.BusinessException;
import com.smartmeeting.mq.KafkaProducer;
import com.smartmeeting.mq.LocalEventBus;
import com.smartmeeting.service.FeishuService;
import com.smartmeeting.model.MinuteGenerateMessage;
import com.smartmeeting.repository.MeetingMapper;
import com.smartmeeting.repository.ParticipantMapper;
import com.smartmeeting.repository.TodoMapper;
import com.smartmeeting.entity.MeetingTodo;
import com.smartmeeting.enums.TodoStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MeetingService {

    private final MeetingMapper meetingMapper;
    private final ParticipantMapper participantMapper;
    private final TodoMapper todoMapper;
    private final FeishuService feishuService;
    private final LocalEventBus localEventBus;
    private final MeetingTypePresetService meetingTypePresetService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Kafka 生产者（生产环境，开发环境可选）
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private KafkaProducer kafkaProducer;

    public MeetingService(MeetingMapper meetingMapper, ParticipantMapper participantMapper,
                          TodoMapper todoMapper, FeishuService feishuService,
                          LocalEventBus localEventBus,
                          MeetingTypePresetService meetingTypePresetService) {
        this.meetingMapper = meetingMapper;
        this.participantMapper = participantMapper;
        this.todoMapper = todoMapper;
        this.feishuService = feishuService;
        this.localEventBus = localEventBus;
        this.meetingTypePresetService = meetingTypePresetService;
    }

    @Transactional
    public MeetingResponse createMeeting(MeetingCreateRequest request) {
        meetingTypePresetService.mergeIntoCreateRequest(request);

        Meeting meeting = new Meeting();
        meeting.setId(UUID.randomUUID().toString());
        meeting.setTitle(request.getTitle());
        meeting.setAgenda(request.getAgenda() != null ? toJson(request.getAgenda()) : null);
        meeting.setCompany(request.getCompany());
        meeting.setDepartment(request.getDepartment());
        meeting.setGroupName(request.getGroupName());
        meeting.setPresetTypeCode(request.getPresetTypeCode());
        meeting.setStatus(MeetingStatus.ISSUE_COLLECTING.name());
        meeting.setCreatorId(request.getCreatorId() != null ? request.getCreatorId() : "system");
        meeting.setRoomId(request.getRoomId());
        meeting.setPreviousMeetingId(request.getPreviousMeetingId());
        meeting.setChatId(request.getChatId());
        meeting.setScheduledTime(request.getScheduledTime());

        meetingMapper.insert(meeting);

        if (request.getParticipants() != null) {
            for (MeetingCreateRequest.ParticipantEntry p : request.getParticipants()) {
                Participant participant = new Participant();
                participant.setId(UUID.randomUUID().toString());
                participant.setMeetingId(meeting.getId());
                participant.setUserId(p.getUserId());
                participant.setName(p.getName());
                participant.setStatus("PENDING");
                participant.setTodoCount(0);
                participant.setCompletedCount(0);
                participant.setVoiceprintReady(false);
                participantMapper.insert(participant);
            }
        }

        log.info("Meeting created: id={}, title={}", meeting.getId(), meeting.getTitle());
        return toResponse(meeting);
    }

    @Transactional
    public MeetingResponse startMeeting(String meetingId) {
        Meeting meeting = meetingMapper.selectById(meetingId);
        if (meeting == null) {
            throw new BusinessException(404, "会议不存在: " + meetingId);
        }

        if (meeting.getPreviousMeetingId() != null) {
            Meeting previous = meetingMapper.selectById(meeting.getPreviousMeetingId());
            if (previous != null) {
                meeting.setStatus(MeetingStatus.REVIEWING.name());
                log.info("Previous meeting found: {}, will send progress card", previous.getId());
                
                // 查询上次会议待办统计
                LambdaQueryWrapper<MeetingTodo> todoWrapper = new LambdaQueryWrapper<>();
                todoWrapper.eq(MeetingTodo::getMeetingId, previous.getId());
                List<MeetingTodo> todos = todoMapper.selectList(todoWrapper);
                
                int completed = (int) todos.stream().filter(t -> t.getStatus().equals(TodoStatus.COMPLETED.name())).count();
                int inProgress = (int) todos.stream().filter(t -> t.getStatus().equals(TodoStatus.IN_PROGRESS.name())).count();
                int delayed = (int) todos.stream().filter(t -> t.getStatus().equals(TodoStatus.DELAYED.name())).count();
                
                // 构建进度通报卡片
                List<Map<String, String>> elements = new ArrayList<>();
                Map<String, String> summary = new HashMap<>();
                summary.put("content", String.format(
                    "## 📊 上次会议待办进度\n\n**%s**\n\n- ✅ 已完成: %d\n- 🔄 进行中: %d\n- ⚠️ 已延期: %d",
                    previous.getTitle(), completed, inProgress, delayed));
                elements.add(summary);
                
                // 延期项高亮
                if (delayed > 0) {
                    Map<String, String> delayedSection = new HashMap<>();
                    StringBuilder delayedText = new StringBuilder("**⚠️ 延期项详情:**\n\n");
                    for (MeetingTodo todo : todos.stream().filter(t -> t.getStatus().equals(TodoStatus.DELAYED.name())).collect(Collectors.toList())) {
                        delayedText.append("- ").append(todo.getContent())
                            .append("（责任人: ").append(todo.getAssigneeName()).append("）\n");
                    }
                    delayedSection.put("content", delayedText.toString());
                    elements.add(delayedSection);
                }
                
                // 推送卡片到会议群聊（优先使用 chatId，否则降级使用 creatorId）
                String targetId = meeting.getChatId() != null ? meeting.getChatId() : meeting.getCreatorId();
                if (meeting.getChatId() == null) {
                    log.warn("No chatId configured for meeting {}, using creatorId as fallback", meeting.getId());
                }
                feishuService.sendCardMessage(targetId, "📊 待办进度通报", elements);
                log.info("Progress card sent for previous meeting: {}", previous.getId());
            } else {
                meeting.setStatus(MeetingStatus.STARTED.name());
            }
        } else {
            meeting.setStatus(MeetingStatus.STARTED.name());
        }

        meeting.setActualStartTime(LocalDateTime.now());
        meetingMapper.updateById(meeting);

        log.info("Meeting started: id={}, status={}", meetingId, meeting.getStatus());
        return toResponse(meeting);
    }

    @Transactional
    public MeetingResponse endMeeting(String meetingId) {
        Meeting meeting = meetingMapper.selectById(meetingId);
        if (meeting == null) {
            throw new BusinessException(404, "会议不存在: " + meetingId);
        }

        // 状态校验：RECORDING/PAUSED/STARTED 可结束
        String currentStatus = meeting.getStatus();
        if (!currentStatus.equals(MeetingStatus.RECORDING.name()) &&
            !currentStatus.equals(MeetingStatus.PAUSED.name()) &&
            !currentStatus.equals(MeetingStatus.STARTED.name())) {
            throw new BusinessException(400, "会议状态不允许结束: " + currentStatus);
        }

        meeting.setStatus(MeetingStatus.PROCESSING.name());
        meeting.setActualEndTime(LocalDateTime.now());
        if (meeting.getActualStartTime() != null) {
            int seconds = (int) java.time.Duration.between(
                    meeting.getActualStartTime(), meeting.getActualEndTime()).getSeconds();
            meeting.setDurationSeconds(Math.max(0, seconds));
        } else {
            meeting.setDurationSeconds(0);
        }
        meetingMapper.updateById(meeting);

        // 触发纪要生成链（Kafka 或 LocalEventBus 降级）
        MinuteGenerateMessage message = MinuteGenerateMessage.builder()
                .meetingId(meetingId)
                .audioPath(meeting.getAudioPath())
                .sentAt(System.currentTimeMillis())
                .build();

        if (kafkaProducer != null) {
            try {
                kafkaProducer.sendMinuteGenerate("meeting.events", message);
                log.info("Kafka message sent for minute generation: meetingId={}", meetingId);
            } catch (Exception e) {
                log.warn("Kafka send failed, fallback to LocalEventBus: {}", e.getMessage());
                localEventBus.publishMeetingEvent(message);
            }
        } else {
            localEventBus.publishMeetingEvent(message);
        }

        log.info("Meeting ended: id={}, duration={}s", meetingId, meeting.getDurationSeconds());
        return toResponse(meeting);
    }

    public MeetingResponse getMeeting(String meetingId) {
        Meeting meeting = meetingMapper.selectById(meetingId);
        if (meeting == null) {
            throw new BusinessException(404, "会议不存在: " + meetingId);
        }
        return toResponse(meeting);
    }

    public List<MeetingResponse> listMeetings(String status, String creatorId, int page, int size) {
        LambdaQueryWrapper<Meeting> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(Meeting::getStatus, status);
        }
        if (creatorId != null) {
            wrapper.eq(Meeting::getCreatorId, creatorId);
        }
        wrapper.orderByDesc(Meeting::getCreatedAt);
        wrapper.last("LIMIT " + (page * size) + "," + size);

        List<Meeting> meetings = meetingMapper.selectList(wrapper);
        return meetings.stream().map(this::toResponse).collect(Collectors.toList());
    }

    public void updateStatus(String meetingId, MeetingStatus status) {
        Meeting meeting = new Meeting();
        meeting.setId(meetingId);
        meeting.setStatus(status.name());
        meetingMapper.updateById(meeting);
        log.info("Meeting status updated: id={}, status={}", meetingId, status);
    }

    /**
     * 查询上次会议待办进度（F-MID-02）
     * 用于会议开始时展示上次待办完成情况
     */
    public PreviousProgressResponse getPreviousProgress(String meetingId) {
        Meeting meeting = meetingMapper.selectById(meetingId);
        if (meeting == null) {
            throw new BusinessException(404, "会议不存在: " + meetingId);
        }

        String previousMeetingId = meeting.getPreviousMeetingId();
        if (previousMeetingId == null) {
            // 首次会议，无上次进度
            return null;
        }

        Meeting previousMeeting = meetingMapper.selectById(previousMeetingId);
        if (previousMeeting == null) {
            log.warn("Previous meeting not found: {}", previousMeetingId);
            return null;
        }

        // 查询上次待办
        LambdaQueryWrapper<MeetingTodo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MeetingTodo::getMeetingId, previousMeetingId);
        List<MeetingTodo> todos = todoMapper.selectList(wrapper);

        int total = todos.size();
        int completed = 0;
        int inProgress = 0;
        int delayed = 0;
        List<DelayedItem> delayedItems = List.of();

        for (MeetingTodo todo : todos) {
            String status = todo.getStatus();
            if (TodoStatus.COMPLETED.name().equals(status)) {
                completed++;
            } else if (TodoStatus.IN_PROGRESS.name().equals(status)) {
                inProgress++;
            } else if (TodoStatus.DELAYED.name().equals(status)) {
                delayed++;
            }
        }

        // 收集延期项详情
        if (delayed > 0) {
            delayedItems = todos.stream()
                    .filter(t -> TodoStatus.DELAYED.name().equals(t.getStatus()))
                    .map(t -> DelayedItem.builder()
                            .content(t.getContent())
                            .assigneeName(t.getAssigneeName())
                            .blockReason(t.getBlockReason())
                            .deadline(t.getDeadline())
                            .build())
                    .collect(Collectors.toList());
        }

        return PreviousProgressResponse.builder()
                .lastMeetingId(previousMeetingId)
                .lastMeetingTitle(previousMeeting.getTitle())
                .lastMeetingTime(previousMeeting.getActualEndTime())
                .totalCount(total)
                .completedCount(completed)
                .inProgressCount(inProgress)
                .delayedCount(delayed)
                .delayedItems(delayedItems)
                .build();
    }

    private MeetingResponse toResponse(Meeting meeting) {
        MeetingResponse resp = new MeetingResponse();
        resp.setId(meeting.getId());
        resp.setTitle(meeting.getTitle());
        resp.setAgenda(meeting.getAgenda() != null ? fromJsonArray(meeting.getAgenda()) : null);
        resp.setCompany(meeting.getCompany());
        resp.setDepartment(meeting.getDepartment());
        resp.setGroupName(meeting.getGroupName());
        resp.setPresetTypeCode(meeting.getPresetTypeCode());
        resp.setStatus(meeting.getStatus());
        resp.setCreatorId(meeting.getCreatorId());
        resp.setChatId(meeting.getChatId());
        resp.setRoomId(meeting.getRoomId());
        resp.setPreviousMeetingId(meeting.getPreviousMeetingId());
        resp.setScheduledTime(meeting.getScheduledTime());
        resp.setActualStartTime(meeting.getActualStartTime());
        resp.setActualEndTime(meeting.getActualEndTime());
        resp.setDurationSeconds(meeting.getDurationSeconds());
        resp.setDocUrl(meeting.getDocUrl());
        resp.setRecordingUrl(meeting.getRecordingUrl());
        resp.setCreatedAt(meeting.getCreatedAt());
        resp.setUpdatedAt(meeting.getUpdatedAt());

        LambdaQueryWrapper<Participant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Participant::getMeetingId, meeting.getId());
        List<Participant> participants = participantMapper.selectList(wrapper);
        resp.setParticipants(participants.stream().map(p -> {
            MeetingResponse.ParticipantDTO dto = new MeetingResponse.ParticipantDTO();
            dto.setUserId(p.getUserId());
            dto.setName(p.getName());
            dto.setStatus(p.getStatus());
            return dto;
        }).collect(Collectors.toList()));

        return resp;
    }

    private String toJson(List<String> list) {
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            log.warn("Failed to serialize agenda", e);
            return null;
        }
    }

    private List<String> fromJsonArray(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Failed to deserialize agenda", e);
            return List.of();
        }
    }
}

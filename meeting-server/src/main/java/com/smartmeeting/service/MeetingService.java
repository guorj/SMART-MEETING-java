package com.smartmeeting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.api.dto.MeetingCreateRequest;
import com.smartmeeting.api.dto.MeetingResponse;
import com.smartmeeting.api.dto.PreviousProgressResponse;
import com.smartmeeting.api.dto.PreviousProgressResponse.DelayedItem;
import com.smartmeeting.api.dto.host.HostAgendaItemDto;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.entity.Participant;
import com.smartmeeting.enums.MeetingStatus;
import com.smartmeeting.exception.BusinessException;
import com.smartmeeting.mq.KafkaProducer;
import com.smartmeeting.mq.LocalEventBus;
import com.smartmeeting.service.FeishuService;
import com.smartmeeting.service.notification.MeetingFeishuNotifier;
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

import java.util.concurrent.CompletableFuture;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 会议生命周期核心服务：创建、启动、结束、查询及状态流转。
 * <p>
 * 主要协作组件：{@link MeetingMapper}、{@link ParticipantMapper}、{@link TodoMapper} 持久化；
 * {@link FeishuService}、{@link MeetingFeishuNotifier} 推送飞书通知；
 * {@link MeetingTypePresetService}、{@link PresetAgendaDocService} 处理预设会序与资料；
 * {@link MeetingProgressAIEnhancer} 分析上次待办进度；{@link MeetingMinuteService} 判断纪要是否存在；
 * {@link LocalEventBus} / {@link KafkaProducer} 触发纪要生成链路。
 */
@Slf4j
@Service
public class MeetingService {

    private final MeetingMapper meetingMapper;
    private final ParticipantMapper participantMapper;
    private final TodoMapper todoMapper;
    private final FeishuService feishuService;
    private final LocalEventBus localEventBus;
    private final MeetingTypePresetService meetingTypePresetService;
    private final AiAgentService aiAgentService;
    private final MeetingProgressAIEnhancer progressAIEnhancer;
    private final PresetAgendaDocService presetAgendaDocService;
    private final MeetingMinuteService meetingMinuteService;
    private final MeetingFeishuNotifier meetingFeishuNotifier;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Kafka 生产者（生产环境，开发环境可选）
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private KafkaProducer kafkaProducer;

    /**
     * 构造会议服务，注入持久层、飞书、AI 增强、纪要及消息总线依赖。
     *
     * @param meetingMapper            会议表 Mapper
     * @param participantMapper        参会人 Mapper
     * @param todoMapper               待办 Mapper
     * @param feishuService            飞书 API 封装
     * @param localEventBus            本地事件总线（Kafka 关闭时使用）
     * @param meetingTypePresetService 会务类型预设服务
     * @param aiAgentService           AI Agent 调用
     * @param progressAIEnhancer       待办进度卡片 AI 增强
     * @param presetAgendaDocService   预设会序飞书资料
     * @param meetingMinuteService     库内纪要读写
     * @param meetingFeishuNotifier    飞书通知门面
     */
    public MeetingService(MeetingMapper meetingMapper, ParticipantMapper participantMapper,
                          TodoMapper todoMapper, FeishuService feishuService,
                          LocalEventBus localEventBus,
                          MeetingTypePresetService meetingTypePresetService,
                          AiAgentService aiAgentService,
                          MeetingProgressAIEnhancer progressAIEnhancer,
                          PresetAgendaDocService presetAgendaDocService,
                          MeetingMinuteService meetingMinuteService,
                          MeetingFeishuNotifier meetingFeishuNotifier) {
        this.meetingMapper = meetingMapper;
        this.participantMapper = participantMapper;
        this.todoMapper = todoMapper;
        this.feishuService = feishuService;
        this.localEventBus = localEventBus;
        this.meetingTypePresetService = meetingTypePresetService;
        this.aiAgentService = aiAgentService;
        this.progressAIEnhancer = progressAIEnhancer;
        this.presetAgendaDocService = presetAgendaDocService;
        this.meetingMinuteService = meetingMinuteService;
        this.meetingFeishuNotifier = meetingFeishuNotifier;
    }

    /**
     * 创建会议并写入参会人列表。
     *
     * @param request 会议创建请求（含主题、会序、参会人、群聊 ID 等）
     * @return 创建后的会议响应 DTO
     */
    @Transactional
    public MeetingResponse createMeeting(MeetingCreateRequest request) {
        meetingTypePresetService.mergeIntoCreateRequest(request);

        Meeting meeting = new Meeting();
        meeting.setId(UUID.randomUUID().toString());
        meeting.setTitle(request.getTitle());
        meeting.setAgenda(request.getAgenda() != null ? toJson(request.getAgenda()) : null);
        meeting.setHostAgenda(hostAgendaItemsToJson(request.getHostAgendaItems()));
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
                participant.setAttendanceMode(
                        p.getAttendanceMode() != null && !p.getAttendanceMode().isBlank()
                                ? p.getAttendanceMode().trim().toUpperCase()
                                : "OFFLINE");
                participant.setTodoCount(0);
                participant.setCompletedCount(0);
                participant.setVoiceprintReady(false);
                participantMapper.insert(participant);
            }
        }

        log.info("Meeting created: id={}, title={}", meeting.getId(), meeting.getTitle());
        return toResponse(meeting);
    }

    /**
     * 启动会议：若有上次会议则进入 REVIEWING 并推送待办进度卡片，否则直接进入 STARTED。
     *
     * @param meetingId 会议 ID
     * @return 更新后的会议响应 DTO
     * @throws BusinessException 会议不存在时抛出 404
     */
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
                
                // 🤖 【环节1介入】调用AI Agent分析待办进度
                List<Map<String, String>> elements;
                try {
                    String aiAnalysis = progressAIEnhancer.analyzeAndEnhance(
                        meeting.getId(), previous.getId(), previous.getTitle(), todos);
                    
                    if (aiAnalysis != null && !aiAnalysis.isEmpty()) {
                        elements = progressAIEnhancer.buildSmartCardElements(aiAnalysis, previous.getTitle());
                        log.info("Smart progress card built with AI analysis: meetingId={}", meeting.getId());
                    } else {
                        elements = progressAIEnhancer.buildFallbackCardElements(previous.getTitle(), completed, inProgress, delayed, todos);
                        log.info("Fallback to simple progress card: meetingId={}", meeting.getId());
                    }
                } catch (Exception e) {
                    log.warn("AI enhancement failed, fallback: {}", e.getMessage());
                    elements = progressAIEnhancer.buildFallbackCardElements(previous.getTitle(), completed, inProgress, delayed, todos);
                }
                
                // 推送卡片到会议群聊（优先使用 chatId，否则降级使用 creatorId）
                String targetId = meeting.getChatId() != null ? meeting.getChatId() : meeting.getCreatorId();
                if (meeting.getChatId() == null) {
                    log.warn("No chatId configured for meeting {}, using creatorId as fallback", meeting.getId());
                }
                meetingFeishuNotifier.sendCardMessage(
                        targetId, "📊 待办进度通报", elements,
                        "TODO_PROGRESS", meeting.getId(),
                        "todo-progress:" + meeting.getId() + ":" + previous.getId());
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

    /**
     * 结束会议：校验状态、计算时长、发布纪要生成消息（Kafka 或 LocalEventBus 降级）。
     *
     * @param meetingId 会议 ID
     * @return 更新后的会议响应 DTO
     * @throws BusinessException 会议不存在（404）或当前状态不允许结束（400）
     */
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

    /**
     * 按 ID 查询会议详情（含参会人列表与会序资料 enrichment）。
     *
     * @param meetingId 会议 ID
     * @return 会议响应 DTO
     * @throws BusinessException 会议不存在时抛出 404
     */
    public MeetingResponse getMeeting(String meetingId) {
        Meeting meeting = meetingMapper.selectById(meetingId);
        if (meeting == null) {
            throw new BusinessException(404, "会议不存在: " + meetingId);
        }
        return toResponse(meeting);
    }

    /**
     * 分页查询会议列表，可按状态与创建人过滤。
     *
     * @param status    会议状态过滤（可为 null 表示不限）
     * @param creatorId 创建人 ID 过滤（可为 null 表示不限）
     * @param page      页码（从 0 开始）
     * @param size      每页条数
     * @return 会议响应 DTO 列表
     */
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

    /**
     * 直接更新会议状态（不触发附加业务逻辑）。
     *
     * @param meetingId 会议 ID
     * @param status    目标状态
     */
    public void updateStatus(String meetingId, MeetingStatus status) {
        Meeting meeting = new Meeting();
        meeting.setId(meetingId);
        meeting.setStatus(status.name());
        meetingMapper.updateById(meeting);
        log.info("Meeting status updated: id={}, status={}", meetingId, status);
    }

    /**
     * 查询上次会议待办进度（F-MID-02），用于会议开始时展示上次待办完成情况。
     *
     * @param meetingId 当前会议 ID
     * @return 上次会议进度统计；无上次会议或上次会议不存在时返回 {@code null}
     * @throws BusinessException 当前会议不存在时抛出 404
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

    /** 将实体转为响应 DTO，并加载参会人、预设会序与飞书资料 enrichment。 */
    private MeetingResponse toResponse(Meeting meeting) {
        MeetingResponse resp = new MeetingResponse();
        resp.setId(meeting.getId());
        resp.setTitle(meeting.getTitle());
        resp.setAgenda(meeting.getAgenda() != null ? fromJsonArray(meeting.getAgenda()) : null);
        List<HostAgendaItemDto> hostItems = hostAgendaItemsFromJson(meeting.getHostAgenda());
        resp.setHostAgendaItems(hostItems);
        if ((hostItems == null || hostItems.isEmpty())
                && meeting.getPresetTypeCode() != null
                && meeting.getPresetTypeCode() >= 1
                && meeting.getPresetTypeCode() <= 5) {
            List<HostAgendaItemDto> fromPreset =
                    meetingTypePresetService.hostAgendaItemsForPresetCode(meeting.getPresetTypeCode());
            if (fromPreset != null && !fromPreset.isEmpty()) {
                resp.setHostAgendaItems(fromPreset);
            }
        }
        if (resp.getHostAgendaItems() != null
                && meeting.getPresetTypeCode() != null
                && meeting.getPresetTypeCode() >= 1
                && meeting.getPresetTypeCode() <= 5) {
            presetAgendaDocService.enrichHostAgendaItems(meeting.getPresetTypeCode(), resp.getHostAgendaItems());
        }
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
        boolean hasDoc = meeting.getDocUrl() != null && !meeting.getDocUrl().isBlank();
        resp.setHasMinute(hasDoc || meetingMinuteService.exists(meeting.getId()));
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
            dto.setAttendanceMode(p.getAttendanceMode());
            dto.setCheckedInAt(p.getCheckedInAt());
            dto.setCheckInSource(p.getCheckInSource());
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

    /** 将主持会序 DTO 列表序列化为 JSON 字符串存入 host_agenda 字段。 */
    private String hostAgendaItemsToJson(List<HostAgendaItemDto> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        try {
            var root = objectMapper.createObjectNode();
            var arr = root.putArray("items");
            for (HostAgendaItemDto dto : items) {
                if (dto == null || dto.getTitle() == null || dto.getTitle().isBlank()) {
                    continue;
                }
                var n = arr.addObject();
                n.put("title", dto.getTitle().trim());
                int min = dto.getMinutes() != null && dto.getMinutes() > 0 ? dto.getMinutes() : 10;
                n.put("minutes", min);
                if (dto.getDetail() != null && !dto.getDetail().isBlank()) {
                    n.put("detail", dto.getDetail().trim());
                }
                if (dto.getFeishuDocs() != null && !dto.getFeishuDocs().isEmpty()) {
                    var docs = n.putArray("feishuDocs");
                    for (com.smartmeeting.api.dto.FeishuDocRefDto ref : dto.getFeishuDocs()) {
                        if (ref == null || ref.getUrl() == null || ref.getUrl().isBlank()) {
                            continue;
                        }
                        var d = docs.addObject();
                        if (ref.getKind() != null && !ref.getKind().isBlank()) {
                            d.put("kind", ref.getKind());
                        }
                        d.put("url", ref.getUrl().trim());
                    }
                } else if (dto.getFeishuDocUrl() != null && !dto.getFeishuDocUrl().isBlank()) {
                    n.put("feishuDocUrl", dto.getFeishuDocUrl().trim());
                }
            }
            if (arr.isEmpty()) {
                return null;
            }
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            log.warn("Failed to serialize host_agenda", e);
            return null;
        }
    }

    /** 从 host_agenda JSON 反序列化主持会序 DTO 列表，兼容旧版 feishuDocToken 字段。 */
    private List<HostAgendaItemDto> hostAgendaItemsFromJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode items = root.path("items");
            if (!items.isArray()) {
                return null;
            }
            List<HostAgendaItemDto> out = new ArrayList<>();
            for (JsonNode n : items) {
                String title = n.path("title").asText("").trim();
                if (title.isEmpty()) {
                    continue;
                }
                HostAgendaItemDto dto = new HostAgendaItemDto();
                dto.setTitle(title);
                dto.setMinutes(n.path("minutes").asInt(10));
                String detail = n.path("detail").asText("").trim();
                if (!detail.isEmpty()) {
                    dto.setDetail(detail);
                }
                JsonNode docsNode = n.path("feishuDocs");
                if (docsNode.isArray() && docsNode.size() > 0) {
                    List<com.smartmeeting.api.dto.FeishuDocRefDto> refs = new ArrayList<>();
                    for (JsonNode d : docsNode) {
                        String url = d.path("url").asText("").trim();
                        if (url.isEmpty()) {
                            url = d.path("feishuDocUrl").asText("").trim();
                        }
                        if (url.isEmpty()) {
                            continue;
                        }
                        String kind = d.path("kind").asText("").trim();
                        refs.add(com.smartmeeting.api.dto.FeishuDocRefDto.builder().kind(kind).url(url).build());
                    }
                    if (!refs.isEmpty()) {
                        dto.setFeishuDocs(refs);
                        dto.setFeishuDocUrl(refs.get(0).getUrl());
                    }
                } else {
                    String docUrl = n.path("feishuDocUrl").asText("").trim();
                    if (docUrl.isEmpty()) {
                        String legacyId = n.path("feishuDocToken").asText("").trim();
                        docUrl = com.smartmeeting.service.feishu.FeishuResourceResolver.legacyDocIdToDocxUrl(legacyId);
                        if (docUrl == null) {
                            docUrl = "";
                        }
                    }
                    if (!docUrl.isEmpty()) {
                        dto.setFeishuDocUrl(docUrl);
                    }
                }
                out.add(dto);
            }
            return out.isEmpty() ? null : out;
        } catch (Exception e) {
            log.warn("Failed to deserialize host_agenda", e);
            return null;
        }
    }
}

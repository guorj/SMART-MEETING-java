package com.smartmeeting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.api.dto.MeetingCreateRequest;
import com.smartmeeting.api.dto.MeetingResponse;
import com.smartmeeting.api.dto.host.HostAgendaItemDto;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.entity.Participant;
import com.smartmeeting.enums.MeetingScenario;
import com.smartmeeting.enums.MeetingStatus;
import com.smartmeeting.exception.BusinessException;
import com.smartmeeting.service.FeishuService;
import com.smartmeeting.service.notification.MeetingFeishuNotifier;
import com.smartmeeting.repository.MeetingMapper;
import com.smartmeeting.repository.ParticipantMapper;
import com.smartmeeting.util.MeetingWebPageUrls;
import com.smartmeeting.statemachine.MeetingEvent;
import com.smartmeeting.statemachine.MeetingStateMachineService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
 * {@link MeetingMinuteService} 判断纪要是否存在；
 * {@link DomainEventPublisher} + Outbox 触发纪要生成链路。
 *
 * <p>会前 matter-progress / 上次待办进度卡片已下线，建会时不再推送相关飞书卡片。
 */
@Slf4j
@Service
public class MeetingService {

    private final MeetingMapper meetingMapper;
    private final ParticipantMapper participantMapper;
    private final FeishuService feishuService;
    private final MeetingTypePresetService meetingTypePresetService;
    private final AiAgentService aiAgentService;
    private final PresetAgendaDocService presetAgendaDocService;
    private final MeetingMinuteService meetingMinuteService;
    private final MeetingPresetTypeResolver presetTypeResolver;
    private final MeetingFeishuNotifier meetingFeishuNotifier;
    private final MeetingStateMachineService meetingStateMachineService;
    private final MeetingScenarioResolver meetingScenarioResolver;
    private final PostMeetingOrchestrator postMeetingOrchestrator;
    private final RecordingService recordingService;
    private final MeetingWebPageUrls meetingWebPageUrls;
    private final MeetingCalendarSyncService meetingCalendarSyncService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 构造会议服务，注入持久层、飞书、AI 增强、纪要及消息总线依赖。
     *
     * @param meetingMapper            会议表 Mapper
     * @param participantMapper        参会人 Mapper
     * @param feishuService            飞书 API 封装
     * @param meetingTypePresetService 会务类型预设服务
     * @param aiAgentService           AI Agent 调用
     * @param presetAgendaDocService   预设会序飞书资料
     * @param meetingMinuteService     库内纪要读写
     * @param presetTypeResolver       会务预设编码解析（转写等子表写入缓存）
     * @param meetingFeishuNotifier    飞书通知门面
     */
    public MeetingService(MeetingMapper meetingMapper, ParticipantMapper participantMapper,
                          FeishuService feishuService,
                          MeetingTypePresetService meetingTypePresetService,
                          AiAgentService aiAgentService,
                          PresetAgendaDocService presetAgendaDocService,
                          MeetingMinuteService meetingMinuteService,
                          MeetingPresetTypeResolver presetTypeResolver,
                          MeetingFeishuNotifier meetingFeishuNotifier,
                          MeetingStateMachineService meetingStateMachineService,
                          MeetingScenarioResolver meetingScenarioResolver,
                          PostMeetingOrchestrator postMeetingOrchestrator,
                          RecordingService recordingService,
                          MeetingWebPageUrls meetingWebPageUrls,
                          MeetingCalendarSyncService meetingCalendarSyncService) {
        this.meetingMapper = meetingMapper;
        this.participantMapper = participantMapper;
        this.feishuService = feishuService;
        this.meetingTypePresetService = meetingTypePresetService;
        this.aiAgentService = aiAgentService;
        this.presetAgendaDocService = presetAgendaDocService;
        this.meetingMinuteService = meetingMinuteService;
        this.presetTypeResolver = presetTypeResolver;
        this.meetingFeishuNotifier = meetingFeishuNotifier;
        this.meetingStateMachineService = meetingStateMachineService;
        this.meetingScenarioResolver = meetingScenarioResolver;
        this.postMeetingOrchestrator = postMeetingOrchestrator;
        this.recordingService = recordingService;
        this.meetingWebPageUrls = meetingWebPageUrls;
        this.meetingCalendarSyncService = meetingCalendarSyncService;
    }

    /**
     * 创建会议并写入参会人列表。
     *
     * @param request 会议创建请求（含主题、会序、参会人、群聊 ID 等）
     * @return 创建后的会议响应 DTO
     */
    @Transactional
    public MeetingResponse createMeeting(MeetingCreateRequest request) {
        Integer presetForRefresh = request.getPresetTypeCode();
        if (presetForRefresh != null && presetForRefresh > 0) {
            presetAgendaDocService.refreshPresetBundle(presetForRefresh);
        }
        meetingTypePresetService.mergeIntoCreateRequest(request);

        Meeting meeting = new Meeting();
        meeting.setId(UUID.randomUUID().toString());
        meeting.setTitle(request.getTitle());
        meeting.setAgenda(request.getAgenda() != null ? toJson(request.getAgenda()) : null);
        Integer presetCode = request.getPresetTypeCode();
        if (presetCode != null && presetCode > 0) {
            meeting.setHostAgenda(presetAgendaDocService.syncHostAgendaForCreate(presetCode, request.getHostAgendaItems()));
        } else {
            meeting.setHostAgenda(hostAgendaItemsToJson(request.getHostAgendaItems()));
        }
        meeting.setCompany(request.getCompany());
        meeting.setDepartment(request.getDepartment());
        meeting.setGroupName(request.getGroupName());
        meeting.setPresetTypeCode(request.getPresetTypeCode());
        meeting.setStatus(MeetingStatus.ISSUE_COLLECTING.name());
        meeting.setCreatorId(request.getCreatorId() != null ? request.getCreatorId() : "system");
        meeting.setRoomId(request.getRoomId());
        MeetingScenario scenario = meetingScenarioResolver.resolve(request.getMeetingScenario(), request.getParticipants());
        meeting.setMeetingScenario(scenario.name());
        meeting.setSourceAudioUrl(request.getSourceAudioUrl());
        meeting.setPreviousMeetingId(request.getPreviousMeetingId());
        meeting.setChatId(request.getChatId());
        if (request.getScheduledTime() != null && !request.getScheduledTime().isAfter(LocalDateTime.now())) {
            throw new BusinessException(400, "计划开始时间须晚于当前时刻");
        }
        meeting.setScheduledTime(request.getScheduledTime());

        meetingMapper.insert(meeting);
        presetTypeResolver.remember(meeting.getId(), meeting.getPresetTypeCode());

        if (request.getParticipants() != null) {
            for (MeetingCreateRequest.ParticipantEntry p : request.getParticipants()) {
                Participant participant = new Participant();
                participant.setId(UUID.randomUUID().toString());
                participant.setMeetingId(meeting.getId());
                participant.setPresetTypeCode(meeting.getPresetTypeCode());
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
     * 启动会议：直接进入 STARTED 状态。
     *
     * <p>会前进度由 feishu-scheduled-bot 定时生成「事项对比通报」
     * （写入 {@code generated_report_url}，主持页只读展示），
     * 建会时不再推送「上次待办进度」飞书卡片。
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

        String status = meeting.getStatus();
        if (MeetingStatus.CANCELLED.name().equals(status)) {
            throw new BusinessException(400, "会议已取消，不能开始: " + meetingId);
        }
        if (!MeetingStatus.ISSUE_COLLECTING.name().equals(status)
                && !MeetingStatus.INVITED.name().equals(status)) {
            throw new BusinessException(400, "仅可开始待开始会议: " + status);
        }

        MeetingEvent event = MeetingStatus.INVITED.name().equals(status)
                ? MeetingEvent.START_MEETING : MeetingEvent.FAST_START;
        meetingStateMachineService.apply(meetingId, event);
        meeting.setStatus(MeetingStatus.STARTED.name());
        meeting.setActualStartTime(LocalDateTime.now());
        meetingMapper.updateById(meeting);

        log.info("Meeting started: id={}, status={}", meetingId, meeting.getStatus());
        return toResponse(meeting);
    }

    /**
     * 取消未开始会议（议题收集中 / 已邀约）。
     *
     * @param meetingId 会议 ID
     * @return 更新后的会议响应 DTO
     * @throws BusinessException 会议不存在（404）或状态不可取消（400）
     */
    @Transactional
    public MeetingResponse cancelDraftMeeting(String meetingId) {
        Meeting meeting = meetingMapper.selectById(meetingId);
        if (meeting == null) {
            throw new BusinessException(404, "会议不存在: " + meetingId);
        }
        String status = meeting.getStatus();
        if (meeting.getActualStartTime() != null) {
            throw new BusinessException(400, "会议已开始，不能取消预约: " + status);
        }
        if (!MeetingStatus.ISSUE_COLLECTING.name().equals(status)
                && !MeetingStatus.INVITED.name().equals(status)) {
            throw new BusinessException(400, "仅可取消未开始的会议: " + status);
        }
        meetingCalendarSyncService.deleteScheduledCalendarEvent(meeting);
        meetingStateMachineService.apply(meetingId, MeetingEvent.CANCEL_MEETING);
        meeting.setStatus(MeetingStatus.CANCELLED.name());
        meetingMapper.updateById(meeting);
        log.info("Meeting draft cancelled: id={}, title={}, creatorId={}, chatId={}, originalStatus={}, scheduledTime={}",
                meetingId, meeting.getTitle(), meeting.getCreatorId(), meeting.getChatId(), status, meeting.getScheduledTime());
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

        meetingStateMachineService.apply(meetingId, MeetingEvent.END_MEETING);
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
        recordingService.clearRecordingState(meetingId);

        String eventAudioSource = recordingService.resolveAndPersistAudioPath(meeting);
        postMeetingOrchestrator.dispatchAfterMeetingEnded(
                meetingId, eventAudioSource, List.of(), null);

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
        meetingStateMachineService.forceStatus(meetingId, status);
        log.info("Meeting status updated: id={}, status={}", meetingId, status);
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
                && meeting.getPresetTypeCode() > 0) {
            List<HostAgendaItemDto> fromPreset =
                    meetingTypePresetService.hostAgendaItemsForPresetCode(meeting.getPresetTypeCode());
            if (fromPreset != null && !fromPreset.isEmpty()) {
                resp.setHostAgendaItems(fromPreset);
            }
        }
        if (resp.getHostAgendaItems() != null
                && meeting.getPresetTypeCode() != null
                && meeting.getPresetTypeCode() > 0) {
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
        resp.setMeetingScenario(meeting.getMeetingScenario());
        resp.setSourceAudioUrl(meeting.getSourceAudioUrl());
        resp.setPreviousMeetingId(meeting.getPreviousMeetingId());
        resp.setScheduledTime(meeting.getScheduledTime());
        resp.setActualStartTime(meeting.getActualStartTime());
        resp.setActualEndTime(meeting.getActualEndTime());
        resp.setDurationSeconds(meeting.getDurationSeconds());
        resp.setDocUrl(meeting.getDocUrl());
        boolean hasDoc = meeting.getDocUrl() != null && !meeting.getDocUrl().isBlank();
        resp.setHasMinute(hasDoc || meetingMinuteService.exists(meeting.getId()));
        resp.setRecordingUrl(meetingWebPageUrls.resolveRecordingPageUrl(
                meeting.getId(), meeting.getRecordingToken(), meeting.getRecordingUrl()));
        resp.setVcMeetingUrl(meeting.getVcMeetingUrl());
        resp.setVcMinuteToken(meeting.getVcMinuteToken());
        resp.setVcRecordingUrl(meeting.getVcRecordingUrl());
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
                if (dto.getExternalUrl() != null && !dto.getExternalUrl().isBlank()) {
                    n.put("externalUrl", dto.getExternalUrl().trim());
                    if (dto.getExternalLinkLabel() != null && !dto.getExternalLinkLabel().isBlank()) {
                        n.put("externalLinkLabel", dto.getExternalLinkLabel().trim());
                    }
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
            int docsV2Items = 0;
            int parsedRefCount = 0;
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
                String externalUrl = n.path("externalUrl").asText("").trim();
                if (!externalUrl.isEmpty()) {
                    dto.setExternalUrl(externalUrl);
                    String externalLabel = n.path("externalLinkLabel").asText("").trim();
                    if (!externalLabel.isEmpty()) {
                        dto.setExternalLinkLabel(externalLabel);
                    }
                }
                JsonNode docsV2 = n.path("docs");
                if (docsV2.isArray() && docsV2.size() > 0) {
                    docsV2Items++;
                    List<com.smartmeeting.api.dto.FeishuDocRefDto> refs = new ArrayList<>();
                    for (JsonNode d : docsV2) {
                        String role = d.path("role").asText("").trim();
                        if (role.isEmpty()) {
                            role = d.path("configRole").asText("").trim();
                        }
                        if (role.isEmpty()) {
                            role = d.path("config_role").asText("").trim();
                        }
                        if (role.isEmpty()) {
                            role = "SOURCE";
                        }
                        String normalizedRole = role.toUpperCase(java.util.Locale.ROOT);
                        if (!"SOURCE".equals(normalizedRole)
                                && !"BOTH".equals(normalizedRole)
                                && !"OUTPUT".equals(normalizedRole)) {
                            continue;
                        }
                        String url = d.path("url").asText("").trim();
                        if (url.isEmpty()) {
                            url = d.path("feishuDocUrl").asText("").trim();
                        }
                        if (url.isEmpty()) {
                            String legacyId = d.path("token").asText("").trim();
                            if (legacyId.isEmpty()) {
                                legacyId = d.path("feishuDocToken").asText("").trim();
                            }
                            url = com.smartmeeting.config.feishu.FeishuResourceResolver.legacyDocIdToDocxUrl(legacyId);
                            if (url == null) {
                                url = "";
                            }
                        }
                        if (url.isEmpty()) {
                            continue;
                        }
                        String kind = d.path("kind").asText("").trim();
                        if (kind.isEmpty()) {
                            kind = d.path("docKind").asText("").trim();
                        }
                        refs.add(com.smartmeeting.api.dto.FeishuDocRefDto.builder().kind(kind).url(url).build());
                    }
                    if (!refs.isEmpty()) {
                        parsedRefCount += refs.size();
                        dto.setFeishuDocs(refs);
                        dto.setFeishuDocUrl(refs.get(0).getUrl());
                    } else {
                        log.warn("hostAgendaItemsFromJson docs[] parsed empty: title={}, docsNodeCount={}",
                                title, docsV2.size());
                    }
                } else {
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
                            docUrl = com.smartmeeting.config.feishu.FeishuResourceResolver.legacyDocIdToDocxUrl(legacyId);
                            if (docUrl == null) {
                                docUrl = "";
                            }
                        }
                        if (!docUrl.isEmpty()) {
                            dto.setFeishuDocUrl(docUrl);
                        }
                    }
                }
                out.add(dto);
            }
            log.info("hostAgendaItemsFromJson parsed: agendaItems={}, docsV2Items={}, parsedRefs={}",
                    out.size(), docsV2Items, parsedRefCount);
            return out.isEmpty() ? null : out;
        } catch (Exception e) {
            log.warn("Failed to deserialize host_agenda", e);
            return null;
        }
    }
}

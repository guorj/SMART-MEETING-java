package com.smartmeeting.service.host;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartmeeting.api.dto.host.HostAgendaItemDto;
import com.smartmeeting.api.dto.host.HostStartRequest;
import com.smartmeeting.constants.HostAgendaConstants;
import com.smartmeeting.api.config.MeetingHostWebSocketHandler;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.entity.Participant;
import com.smartmeeting.entity.MeetingTypePreset;
import com.smartmeeting.enums.AttendanceMode;
import com.smartmeeting.enums.CheckInSource;
import com.smartmeeting.repository.ParticipantMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmeeting.exception.BusinessException;
import com.smartmeeting.entity.MatterProgressDocConfig;
import com.smartmeeting.repository.MeetingMapper;
import com.smartmeeting.repository.MeetingTypePresetMapper;
import com.smartmeeting.api.dto.FeishuDocRefDto;
import com.smartmeeting.service.PresetAgendaDocService;
import com.smartmeeting.service.feishu.FeishuDocRefs;
import com.smartmeeting.service.feishu.FeishuResourceRef;
import com.smartmeeting.service.feishu.FeishuResourceResolver;
import com.smartmeeting.tts.XfyunOnlineTtsSynthesizeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * AI 会议主持会话：单会议维度的议题进度、倒计时、飞书群静音、主持端 WebSocket 状态推送与讯飞 TTS 播报编排。
 * <p>
 * 主持页订阅 {@code host_state} 与 {@code tts_*} 消息；本服务负责生成话术文本、合成 PCM 后经 {@link MeetingHostWebSocketHandler} 下发。
 * 检点流程与 ASR 答到判定见 {@link #onRollCallFinalTranscript}、{@link #isRollCallAnswerWindowArmed}。
 */
@Slf4j
@Service
public class MeetingHostSessionService {
    /** 主持 TTS 下发与口型分析约定的采样率（Hz），须与 {@link XfyunOnlineTtsSynthesizeService} 及前端解码一致 */
    private static final int PCM_SAMPLE_RATE = 16000;
    private static final int PCM_BYTES_PER_SAMPLE = 2;
    /** 答到 deadline 起算时，在 PCM 估算时长之外再垫的毫秒，吸收网络/调度抖动 */
    private static final long ROLL_CALL_ARM_EXTRA_MS = 300L;
    /**
     * 多段 TTS 串联时，在「服务端估算的播放时长」之外再留的尾量（毫秒），避免 deadline 早于真实听完时间。
     * 主持页已对 PCM 排队播放；本值仍用于开场后自动检点、检点收尾后自动下一议题等调度延迟。
     */
    private static final long HOST_TTS_CLIENT_PLAYBACK_TAIL_MS = 600L;

    private final MeetingMapper meetingMapper;
    private final ParticipantMapper participantMapper;
    private final MeetingTypePresetMapper presetMapper;
    private final PresetAgendaDocService presetAgendaDocService;
    private final MeetingHostFeishuMuteRegistry muteRegistry;
    private final MeetingHostWebSocketHandler hostWebSocketHandler;
    private final XfyunOnlineTtsSynthesizeService ttsSynthesizeService;
    private final ObjectMapper objectMapper;

    @Value("${meeting.host.enabled:true}")
    private boolean hostEnabled;

    @Value("${meeting.host.reminder.topic-minutes-left:3}")
    private int topicWarnMinutes;

    @Value("${meeting.host.reminder.meeting-minutes-left:10,5}")
    private String meetingWarnMinutesCsv;

    /** 每人「请某某答到」播报结束后的基础作答秒数（至少 5），不含 ASR 定稿缓冲 */
    @Value("${meeting.host.roll-call.window-seconds:12}")
    private int rollCallWindowSeconds;

    /** 在基础窗口之上追加的秒数，缓解定稿滞后导致的漏记；与答到判定、未到超时共用同一 deadline */
    @Value("${meeting.host.roll-call.asr-grace-seconds:6}")
    private int hostRollCallAsrGraceSeconds;

    /** 线上参会人打开个人链接盘点的等待秒数，结束后进入线下逐一点名 */
    @Value("${meeting.host.roll-call.online-inventory-seconds:60}")
    private int rollCallOnlineInventorySeconds;

    /** 主持页「议题加时」可选分钟数 */
    private static final Set<Integer> ALLOWED_TOPIC_EXTEND_MINUTES = Set.of(1, 3, 5, 10);

    /** 每会议一把锁：检点与 ASR 回调与 tick 并发修改同一 {@link HostRuntime} 时串行化 */
    private final Map<String, Object> runtimeLocks = new ConcurrentHashMap<>();

    /** 秒级 tick（议题/会议剩余、检点超时、推送状态），线程数为 2 避免与 TTS supplyAsync 过度争用 */
    private final ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(2, r -> {
        Thread t = new Thread(r, "meeting-host-tick");
        t.setDaemon(true);
        return t;
    });

    /** meetingId → 主持运行时；会议结束或 teardown 时由 {@link #stopAndClear} 移除 */
    private final Map<String, HostRuntime> runtimes = new ConcurrentHashMap<>();

    /**
     * 构造主持会话服务，注入会议/参会人持久层、预设议程与飞书静音、WebSocket 推送及 TTS 合成依赖。
     *
     * @param meetingMapper           会议表，用于加载 chatId、host_agenda、preset 等
     * @param participantMapper       参会人表，检点名单优先从此加载
     * @param presetMapper            会务类型预设（议程模板、应到名单 participants_names）
     * @param presetAgendaDocService  预设会序飞书资料配置合并
     * @param muteRegistry            飞书群静音/恢复
     * @param hostWebSocketHandler    主持端 WebSocket，推送 host_state 与 TTS 帧
     * @param ttsSynthesizeService    讯飞在线合成，产出 16k s16le PCM
     * @param objectMapper            JSON 序列化（状态、TTS 消息体）
     */
    public MeetingHostSessionService(MeetingMapper meetingMapper,
                                     ParticipantMapper participantMapper,
                                     MeetingTypePresetMapper presetMapper,
                                     PresetAgendaDocService presetAgendaDocService,
                                     MeetingHostFeishuMuteRegistry muteRegistry,
                                     @Lazy MeetingHostWebSocketHandler hostWebSocketHandler,
                                     XfyunOnlineTtsSynthesizeService ttsSynthesizeService,
                                     ObjectMapper objectMapper) {
        this.meetingMapper = meetingMapper;
        this.participantMapper = participantMapper;
        this.presetMapper = presetMapper;
        this.presetAgendaDocService = presetAgendaDocService;
        this.muteRegistry = muteRegistry;
        this.hostWebSocketHandler = hostWebSocketHandler;
        this.ttsSynthesizeService = ttsSynthesizeService;
        this.objectMapper = objectMapper;
    }

    /**
     * 返回指定会议在进程内的互斥锁对象（非可重入；同 meetingId 共用一个锁实例）。
     *
     * @param meetingId 会议主键
     * @return 该会议专用锁，永不为 null
     */
    private Object lockFor(String meetingId) {
        return runtimeLocks.computeIfAbsent(meetingId, k -> new Object());
    }

    /**
     * 是否已存在主持会话（已开始会议且未 {@link #stopAndClear}）。
     *
     * @param meetingId 会议主键
     * @return 若 {@code runtimes} 中含该 meetingId 则为 true
     */
    public boolean isActive(String meetingId) {
        return runtimes.containsKey(meetingId);
    }

    /**
     * 主持进行中：返回指定会序已绑定的全部飞书资料引用（供主持页展示与 agenda-doc-content API 拉取正文）。
     * <p>
     * 优先从内存运行时 {@link HostTopic} 读取；若无活跃会话或未绑定，则回退至
     * {@link PresetAgendaDocService#listDocRefsForAgenda}。
     *
     * @param meetingId   会议主键
     * @param agendaIndex 会序下标（从 0 起）
     * @return 飞书资料 DTO 列表；会议不存在或无配置时返回空列表，永不为 null
     */
    public List<FeishuDocRefDto> getAgendaDocRefs(String meetingId, int agendaIndex) {
        HostRuntime rt = runtimes.get(meetingId);
        if (rt != null && agendaIndex >= 0 && agendaIndex < rt.topics.size()) {
            List<FeishuDocRefDto> fromTopic = topicToDocRefDtos(rt.topics.get(agendaIndex));
            if (!fromTopic.isEmpty()) {
                return fromTopic;
            }
        }
        Meeting meeting = meetingMapper.selectById(meetingId);
        if (meeting == null) {
            return List.of();
        }
        return presetAgendaDocService.listDocRefsForAgenda(meeting, agendaIndex);
    }

    /**
     * 返回指定会序的首条飞书资料引用（兼容旧 API）。
     *
     * @param meetingId   会议主键
     * @param agendaIndex 会序下标（从 0 起）
     * @return 首条资料引用；无资料时为 null
     * @deprecated 请使用 {@link #getAgendaDocRefs}
     */
    public AgendaDocRef getAgendaDocRef(String meetingId, int agendaIndex) {
        List<FeishuDocRefDto> refs = getAgendaDocRefs(meetingId, agendaIndex);
        if (refs.isEmpty()) {
            return null;
        }
        FeishuDocRefDto first = refs.get(0);
        return new AgendaDocRef(
                first.getUrl() != null ? first.getUrl() : "",
                first.getKind() != null ? first.getKind() : "");
    }

    /**
     * 将运行时议题上的飞书绑定转为 API DTO 列表（兼容单条 feishuDocUrl 与多条 feishuDocs）。
     *
     * @param t 主持议程项，可为 null
     * @return DTO 列表，无资料时为空列表
     */
    private static List<FeishuDocRefDto> topicToDocRefDtos(HostTopic t) {
        if (t == null) {
            return List.of();
        }
        if (t.feishuDocs != null && !t.feishuDocs.isEmpty()) {
            return t.feishuDocs.stream()
                    .map(b -> FeishuDocRefDto.builder().kind(b.kind).url(b.url).build())
                    .toList();
        }
        if (t.feishuDocUrl == null || t.feishuDocUrl.isBlank()) {
            return List.of();
        }
        return List.of(FeishuDocRefDto.builder()
                .kind(t.feishuDocKind)
                .url(t.feishuDocUrl.trim())
                .build());
    }

    /**
     * 会序绑定的单条飞书资料引用（docx / wiki / base）。
     *
     * @param feishuDocUrl  飞书资料浏览器链接
     * @param feishuDocKind 资料类型标识（如 DOCX、WIKI、BASE）
     */
    public record AgendaDocRef(String feishuDocUrl, String feishuDocKind) {
    }

    /**
     * 开启主持会话：加载议程、静音飞书群、启动 tick、播报开场白；若预设有应到名单则开场播完后调度自动检点。
     *
     * @param meetingId 会议主键，须与 JWT/主持 token 中会议一致
     * @param body      可选；非空且含 {@code items} 时优先用请求体议程，否则走预设 1～5 / 会议 host_agenda / 默认 JSON / 硬编码
     * @throws BusinessException 主持未启用、会话已存在、会议不存在、议程为空等
     */
    public void start(String meetingId, HostStartRequest body) {
        if (!hostEnabled) {
            throw new BusinessException(400, "AI 会议主持人功能未启用");
        }
        if (runtimes.containsKey(meetingId)) {
            throw new BusinessException(400, "主持会话已在进行中");
        }
        Meeting meeting = meetingMapper.selectById(meetingId);
        if (meeting == null) {
            throw new BusinessException(404, "会议不存在: " + meetingId);
        }
        List<HostTopic> topics = resolveTopics(meeting, body);
        if (topics.isEmpty()) {
            throw new BusinessException(400, "议题为空：请提供 items；preset 1-5 时配置 int_meeting_type_preset.host_agenda；否则配置 int_meeting.host_agenda 或使用默认模板");
        }
        sanitizeTopicsFeishuRefs(topics);
        mergePresetAgendaDocs(meeting.getPresetTypeCode(), topics, meetingId);

        muteRegistry.muteChat(meeting.getChatId());

        long now = System.currentTimeMillis();
        HostRuntime rt = new HostRuntime();
        rt.meetingId = meetingId;
        rt.chatId = meeting.getChatId();
        rt.topics = topics;
        rt.currentIndex = 0;
        rt.topics.get(0).status = "RUNNING";
        int firstMin = Math.max(1, rt.topics.get(0).minutes);
        int totalMin = topics.stream().mapToInt(t -> Math.max(1, t.minutes)).sum();
        rt.topicEndMs = now + firstMin * 60_000L;
        rt.meetingEndMs = now + totalMin * 60_000L;
        rt.paused = false;
        rt.pauseStartedAtMs = 0;
        rt.lastTopicLeftSec = Integer.MAX_VALUE;
        rt.lastMeetingLeftSec = Integer.MAX_VALUE;
        rt.topicTimeUpAnnounced = false;

        rt.tick = scheduler.scheduleAtFixedRate(() -> safeTick(meetingId), 1, 1, TimeUnit.SECONDS);
        runtimes.put(meetingId, rt);

        pushHostState(meetingId);
        String openingLine = "会议开始。当前进行：" + rt.topics.get(0).title + "，预计 " + firstMin + " 分钟。";
        if (canAutoStartRollCall(meeting)) {
            final String mid = meetingId;
            speakAsyncFutureWithDurationMs(meetingId, openingLine).thenAccept(openingDurationMs -> {
                long waitMs = Math.max(0L, openingDurationMs) + HOST_TTS_CLIENT_PLAYBACK_TAIL_MS;
                scheduler.schedule(() -> {
                    try {
                        startRollCall(mid);
                    } catch (Exception e) {
                        log.warn("Auto roll-call after host opening failed: {}", e.getMessage());
                    }
                }, waitMs, TimeUnit.MILLISECONDS);
            });
        } else {
            speakAsync(meetingId, openingLine);
        }
    }

    /**
     * 会务预设中是否配置了非空应到名单（用于开场后是否自动进入检点）。
     *
     * @param meeting 当前会议实体（读取 presetTypeCode 等）
     * @return 若能解析出至少一名应到人员则为 true；名单不可用或业务校验失败则为 false（不抛异常）
     */
    private boolean canAutoStartRollCall(Meeting meeting) {
        try {
            List<RollCallPerson> people = loadRollCallPeopleFromMeeting(meeting);
            return people != null && !people.isEmpty();
        } catch (BusinessException ex) {
            return false;
        }
    }

    /**
     * 包装 {@link #tick}，避免单次异常打垮调度线程。
     *
     * @param meetingId 会议主键
     */
    private void safeTick(String meetingId) {
        try {
            tick(meetingId);
        } catch (Exception e) {
            log.warn("Host tick error meetingId={}: {}", meetingId, e.getMessage());
        }
    }

    /**
     * 每秒：更新议题/会议剩余并可能播报提醒；议题到时仅提示不自动切题；推进检点超时；广播最新 {@code host_state}。
     *
     * @param meetingId 会议主键；若已无 runtime 或处于暂停则直接返回
     */
    private void tick(String meetingId) {
        HostRuntime rt = runtimes.get(meetingId);
        if (rt == null || rt.paused) {
            return;
        }
        long now = System.currentTimeMillis();
        long topicLeftMs = Math.max(0, rt.topicEndMs - now);
        long meetingLeftMs = Math.max(0, rt.meetingEndMs - now);
        int topicLeftSec = (int) (topicLeftMs / 1000);
        int meetingLeftSec = (int) (meetingLeftMs / 1000);

        int tw = Math.max(1, topicWarnMinutes) * 60;
        if (rt.lastTopicLeftSec > tw && topicLeftSec <= tw) {
            speakAsync(meetingId, "当前议题还剩 " + topicWarnMinutes + " 分钟。");
        }
        rt.lastTopicLeftSec = topicLeftSec;

        for (int m : defaultMeetingWarns()) {
            int sec = m * 60;
            if (rt.lastMeetingLeftSec > sec && meetingLeftSec <= sec) {
                speakAsync(meetingId, "会议还剩 " + m + " 分钟。");
                break;
            }
        }
        rt.lastMeetingLeftSec = meetingLeftSec;

        if (topicLeftSec == 0 && !rt.topicTimeUpAnnounced) {
            rt.topicTimeUpAnnounced = true;
            speakAsync(meetingId, "本议题时间到。请点击「下一议题」继续，或继续讨论后再切换。");
        }

        rollCallOnlineInventoryMaybeTimeout(meetingId, rt, now);
        rollCallMaybeTimeout(meetingId, rt, now);

        pushHostState(meetingId);
    }

    /**
     * 解析配置 {@code meeting.host.reminder.meeting-minutes-left}，得到会议剩余分钟提醒阈值列表（降序）。
     *
     * @return 正整数分钟列表，如配置为空则默认 [10, 5]
     */
    private List<Integer> defaultMeetingWarns() {
        List<Integer> out = new ArrayList<>();
        if (meetingWarnMinutesCsv != null) {
            for (String p : meetingWarnMinutesCsv.split(",")) {
                try {
                    int v = Integer.parseInt(p.trim());
                    if (v > 0) {
                        out.add(v);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (out.isEmpty()) {
            out.add(10);
            out.add(5);
        }
        out.sort(Collections.reverseOrder());
        return out;
    }

    /**
     * 暂停：冻结议题/会议结束时刻与检点 deadline（若已起算），便于与录音暂停对齐。
     *
     * @param meetingId 会议主键
     * @throws BusinessException 未开启主持会话
     */
    public void pause(String meetingId) {
        HostRuntime rt = runtimes.get(meetingId);
        if (rt == null) {
            throw new BusinessException(400, "未开启主持会话");
        }
        if (rt.paused) {
            return;
        }
        rt.paused = true;
        rt.pauseStartedAtMs = System.currentTimeMillis();
        pushHostState(meetingId);
    }

    /**
     * 继续：把暂停时长补回议题/会议结束时刻及检点 deadline。
     *
     * @param meetingId 会议主键
     * @throws BusinessException 未开启主持会话
     */
    public void resume(String meetingId) {
        HostRuntime rt = runtimes.get(meetingId);
        if (rt == null) {
            throw new BusinessException(400, "未开启主持会话");
        }
        if (!rt.paused) {
            return;
        }
        if (rt.pauseStartedAtMs > 0) {
            long extra = System.currentTimeMillis() - rt.pauseStartedAtMs;
            rt.topicEndMs += extra;
            rt.meetingEndMs += extra;
            if (isRollCallOfflinePhase(rt.rollCallPhase) && rt.rollCallDeadlineMs > 0) {
                rt.rollCallDeadlineMs += extra;
            }
            if ("ONLINE_INVENTORY".equals(rt.rollCallPhase) && rt.rollCallOnlineInventoryDeadlineMs > 0) {
                rt.rollCallOnlineInventoryDeadlineMs += extra;
            }
        }
        rt.paused = false;
        rt.pauseStartedAtMs = 0;
        pushHostState(meetingId);
    }

    /**
     * 将当前议题标为完成并进入下一项 RUNNING，重置本议题计时与提醒状态。
     *
     * @param meetingId 会议主键；若已在最后一项之后则 no-op
     * @throws BusinessException 未开启主持会话
     */
    public void nextTopic(String meetingId) {
        HostRuntime rt = runtimes.get(meetingId);
        if (rt == null) {
            throw new BusinessException(400, "未开启主持会话");
        }
        if (rt.currentIndex >= rt.topics.size()) {
            return;
        }
        rt.topics.get(rt.currentIndex).status = "COMPLETED";
        rt.currentIndex++;
        rt.topicTimeUpAnnounced = false;
        rt.lastTopicLeftSec = Integer.MAX_VALUE;
        if (rt.currentIndex >= rt.topics.size()) {
            speakAsync(meetingId, "全部议题已结束。您可点击「结束会议」完成录音与纪要生成。");
            pushHostState(meetingId);
            return;
        }
        HostTopic next = rt.topics.get(rt.currentIndex);
        next.status = "RUNNING";
        int min = Math.max(1, next.minutes);
        rt.topicEndMs = System.currentTimeMillis() + min * 60_000L;
        speakAsync(meetingId, "现在进入：" + next.title + "，预计 " + min + " 分钟。");
        pushHostState(meetingId);
    }

    /**
     * 将当前议题标为跳过并进入下一项，语义上同「未完成即切走」。
     *
     * @param meetingId 会议主键；若已在最后一项之后则 no-op
     * @throws BusinessException 未开启主持会话
     */
    public void skipTopic(String meetingId) {
        HostRuntime rt = runtimes.get(meetingId);
        if (rt == null) {
            throw new BusinessException(400, "未开启主持会话");
        }
        if (rt.currentIndex >= rt.topics.size()) {
            return;
        }
        rt.topics.get(rt.currentIndex).status = "SKIPPED";
        rt.currentIndex++;
        rt.topicTimeUpAnnounced = false;
        rt.lastTopicLeftSec = Integer.MAX_VALUE;
        if (rt.currentIndex >= rt.topics.size()) {
            speakAsync(meetingId, "已跳过剩余项。您可点击「结束会议」。");
            pushHostState(meetingId);
            return;
        }
        HostTopic next = rt.topics.get(rt.currentIndex);
        next.status = "RUNNING";
        int min = Math.max(1, next.minutes);
        rt.topicEndMs = System.currentTimeMillis() + min * 60_000L;
        speakAsync(meetingId, "跳过当前会序。现在进入：" + next.title + "，预计 " + min + " 分钟。");
        pushHostState(meetingId);
    }

    /**
     * 为当前议题与整场会议同步加时：延长 {@code topicEndMs}、{@code meetingEndMs}，并累加当前项预计分钟数。
     *
     * @param meetingId 会议主键
     * @param minutes     加时分钟，须为 1、3、5、10 之一
     * @throws BusinessException 未开启会话、无进行中议题、分钟数非法等
     */
    public void extendTopicTime(String meetingId, int minutes) {
        if (!ALLOWED_TOPIC_EXTEND_MINUTES.contains(minutes)) {
            throw new BusinessException(400, "加时分钟数仅支持：1、3、5、10");
        }
        HostRuntime rt = runtimes.get(meetingId);
        if (rt == null) {
            throw new BusinessException(400, "未开启主持会话");
        }
        if (rt.currentIndex < 0 || rt.currentIndex >= rt.topics.size()) {
            throw new BusinessException(400, "当前无进行中的议题");
        }
        long addMs = minutes * 60_000L;
        rt.topicEndMs += addMs;
        rt.meetingEndMs += addMs;
        HostTopic cur = rt.topics.get(rt.currentIndex);
        cur.minutes = Math.max(1, cur.minutes) + minutes;
        rt.topicTimeUpAnnounced = false;
        rt.lastTopicLeftSec = Integer.MAX_VALUE;
        speakAsync(meetingId, "已为当前议题延长 " + minutes + " 分钟。");
        pushHostState(meetingId);
    }

    /**
     * 开始混合检点：线上先按个人链接自动盘点，再对线下参会人逐一点名（ASR 答到）。
     *
     * @param meetingId 会议主键
     * @throws BusinessException 未开启主持会话、检点已在进行、会议不存在、应到名单为空等
     */
    public void startRollCall(String meetingId) {
        synchronized (lockFor(meetingId)) {
            HostRuntime rt = runtimes.get(meetingId);
            if (rt == null) {
                throw new BusinessException(400, "请先开启主持会话");
            }
            if (isRollCallActive(rt.rollCallPhase)) {
                throw new BusinessException(400, "会议检点进行中");
            }
            Meeting meeting = meetingMapper.selectById(meetingId);
            if (meeting == null) {
                throw new BusinessException(404, "会议不存在: " + meetingId);
            }
            List<RollCallPerson> people = loadRollCallPeopleFromMeeting(meeting);
            if (people.isEmpty()) {
                throw new BusinessException(400, "应到名单为空：请配置参会人或在预设中填写 participants_names");
            }
            rt.rollCallPeople.clear();
            rt.rollCallPeople.addAll(people);
            rt.rollCallIndex = -1;
            rt.rollCallDeadlineMs = 0;
            boolean hasOnlinePending = people.stream().anyMatch(RollCallPerson::isOnlinePending);
            if (hasOnlinePending) {
                rt.rollCallPhase = "ONLINE_INVENTORY";
                rt.rollCallOnlineInventoryDeadlineMs =
                        System.currentTimeMillis() + Math.max(15, rollCallOnlineInventorySeconds) * 1000L;
                pushHostState(meetingId);
                final String mid = meetingId;
                speakAsyncFutureWithDurationMs(meetingId, buildHybridRollCallIntroText(people))
                        .thenAccept(durationMs -> scheduler.schedule(
                                () -> beginOfflineRollCallIfStillInventory(mid),
                                Math.max(0L, durationMs) + HOST_TTS_CLIENT_PLAYBACK_TAIL_MS,
                                TimeUnit.MILLISECONDS));
            } else {
                beginOfflineRollCall(meetingId, rt, buildOfflineOnlyIntroText(people));
            }
        }
    }

    /**
     * 线上参会人通过个人链接 check-in 后同步到主持检点名单。
     * <p>
     * 由 {@link com.smartmeeting.service.ParticipantCheckInService} 在登记成功后调用；
     * 无活跃主持会话或检点名单为空时静默忽略。
     *
     * @param meetingId   会议主键
     * @param userId      参会人飞书 userId 或业务标识
     * @param displayName 显示姓名，用于 userId 缺失时的兜底匹配
     */
    public void recordOnlineCheckIn(String meetingId, String userId, String displayName) {
        synchronized (lockFor(meetingId)) {
            HostRuntime rt = runtimes.get(meetingId);
            if (rt == null || rt.rollCallPeople.isEmpty()) {
                return;
            }
            for (RollCallPerson p : rt.rollCallPeople) {
                if ((userId != null && userId.equals(p.userId))
                        || (displayName != null && displayName.equals(p.name))) {
                    p.status = "ANSWERED";
                    p.checkInSource = CheckInSource.AUTO_ONLINE.name();
                    log.info("online check-in: meetingId={}, name={}", meetingId, p.name);
                    break;
                }
            }
            pushHostState(meetingId);
        }
    }

    /**
     * 跳过当前待答到人员（记为 SKIPPED，进入下一位点名）。
     *
     * @param meetingId 会议主键
     * @throws BusinessException 未开启会话、当前不在检点点名中、当前人非 PENDING 等
     */
    public void skipCurrentRollCall(String meetingId) {
        synchronized (lockFor(meetingId)) {
            HostRuntime rt = runtimes.get(meetingId);
            if (rt == null) {
                throw new BusinessException(400, "请先开启主持会话");
            }
            if (!isRollCallOfflinePhase(rt.rollCallPhase) || rt.rollCallIndex < 0
                    || rt.rollCallIndex >= rt.rollCallPeople.size()) {
                throw new BusinessException(400, "当前不在线下检点点名中");
            }
            RollCallPerson cur = rt.rollCallPeople.get(rt.rollCallIndex);
            if (!"PENDING".equals(cur.status)) {
                return;
            }
            cur.status = "SKIPPED";
            advanceRollCallAfterCurrentResolved(meetingId, rt, "已跳过" + cur.name + "。");
        }
    }

    /**
     * ASR 转写回调（应在定稿 final 文本上调用）：若处于某人答到窗口内且文本像答到，则记该人为到会并进入下一位。
     * 与 {@link #isRollCallAnswerWindowArmed(String)} 条件一致，避免 TTS 播报期间误计（deadline 未起算前不接收）。
     *
     * @param meetingId 会议主键
     * @param text      定稿转写全文（已 trim 与否由调用方决定；本方法对 null/blank 直接忽略）
     */
    public void onRollCallFinalTranscript(String meetingId, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        synchronized (lockFor(meetingId)) {
            HostRuntime rt = runtimes.get(meetingId);
            if (rt == null || !isRollCallOfflinePhase(rt.rollCallPhase)) {
                return;
            }
            if (rt.rollCallIndex < 0 || rt.rollCallIndex >= rt.rollCallPeople.size()) {
                return;
            }
            RollCallPerson cur = rt.rollCallPeople.get(rt.rollCallIndex);
            if (!cur.isOfflinePending()) {
                return;
            }
            if (rt.rollCallDeadlineMs <= 0) {
                return;
            }
            long now = System.currentTimeMillis();
            if (now > rt.rollCallDeadlineMs) {
                return;
            }
            if (!"PENDING".equals(cur.status)) {
                return;
            }
            String preview = text.length() > 80 ? text.substring(0, 80) + "...(truncated)" : text;
            boolean matched = RollCallAffirmationMatcher.matches(text);
            if (!matched) {
                log.debug("roll-call no-match: meetingId={}, idx={}, name={}, text='{}'",
                        meetingId, rt.rollCallIndex, cur.name, preview);
                return;
            }
            log.info("roll-call matched: meetingId={}, idx={}, name={}, text='{}'",
                    meetingId, rt.rollCallIndex, cur.name, preview);
            cur.status = "ANSWERED";
            cur.checkInSource = CheckInSource.ROLL_CALL.name();
            advanceRollCallAfterCurrentResolved(meetingId, rt, "收到。");
        }
    }

    /**
     * 是否处于会议检点「当前人、答到窗口已起算且未过期」阶段；供 ASR 仅在此时对尾包做零填充上送，避免影响整场转写帧节奏。
     *
     * @param meetingId 会议主键
     * @return 当 phase=ACTIVE、索引合法、deadline&gt;0 且未过期、当前人为 PENDING 时为 true
     */
    public boolean isRollCallAnswerWindowArmed(String meetingId) {
        synchronized (lockFor(meetingId)) {
            HostRuntime rt = runtimes.get(meetingId);
            if (rt == null || !isRollCallOfflinePhase(rt.rollCallPhase)) {
                return false;
            }
            if (rt.rollCallIndex < 0 || rt.rollCallIndex >= rt.rollCallPeople.size()) {
                return false;
            }
            if (rt.rollCallDeadlineMs <= 0) {
                return false;
            }
            long now = System.currentTimeMillis();
            if (now > rt.rollCallDeadlineMs) {
                return false;
            }
            RollCallPerson cur = rt.rollCallPeople.get(rt.rollCallIndex);
            return cur.isOfflinePending();
        }
    }

    private static boolean isRollCallActive(String phase) {
        return "ONLINE_INVENTORY".equals(phase) || isRollCallOfflinePhase(phase) || "ACTIVE".equals(phase);
    }

    private static boolean isRollCallOfflinePhase(String phase) {
        return "OFFLINE_ROLL_CALL".equals(phase) || "ACTIVE".equals(phase);
    }

    /**
     * 线上盘点阶段超时：将仍未答到的线上参会人记为 MISSED，并进入线下逐一点名。
     *
     * @param meetingId 会议主键
     * @param rt        当前主持运行时
     * @param now       当前 epoch 毫秒
     */
    private void rollCallOnlineInventoryMaybeTimeout(String meetingId, HostRuntime rt, long now) {
        if (!"ONLINE_INVENTORY".equals(rt.rollCallPhase)) {
            return;
        }
        if (rt.rollCallOnlineInventoryDeadlineMs <= 0 || now < rt.rollCallOnlineInventoryDeadlineMs) {
            return;
        }
        synchronized (lockFor(meetingId)) {
            rt = runtimes.get(meetingId);
            if (rt == null || !"ONLINE_INVENTORY".equals(rt.rollCallPhase)) {
                return;
            }
            for (RollCallPerson p : rt.rollCallPeople) {
                if (p.isOnlinePending()) {
                    p.status = "MISSED";
                    p.checkInSource = CheckInSource.TIMEOUT.name();
                }
            }
            beginOfflineRollCall(meetingId, rt, "线上盘点时间到。");
        }
    }

    /** TTS 开场白播完后，若仍处于线上盘点阶段则自动切入线下点名。 */
    private void beginOfflineRollCallIfStillInventory(String meetingId) {
        synchronized (lockFor(meetingId)) {
            HostRuntime rt = runtimes.get(meetingId);
            if (rt == null || !"ONLINE_INVENTORY".equals(rt.rollCallPhase)) {
                return;
            }
            beginOfflineRollCall(meetingId, rt, null);
        }
    }

    /**
     * 进入线下逐一点名：设置 phase、定位首位待答到线下人员，播报开场与首条点名片断并调度答到窗口。
     *
     * @param meetingId 会议主键
     * @param rt        主持运行时
     * @param prelude   可选前缀话术（如「线上盘点时间到。」），可为 null
     */
    private void beginOfflineRollCall(String meetingId, HostRuntime rt, String prelude) {
        rt.rollCallPhase = "OFFLINE_ROLL_CALL";
        rt.rollCallOnlineInventoryDeadlineMs = 0;
        int nextIdx = findNextOfflineRollCallIndex(rt.rollCallPeople, 0);
        if (nextIdx < 0) {
            finishRollCallDone(meetingId, rt, prelude != null ? prelude : "");
            return;
        }
        rt.rollCallIndex = nextIdx;
        rt.rollCallDeadlineMs = 0;
        pushHostState(meetingId);
        RollCallPerson first = rt.rollCallPeople.get(nextIdx);
        final String mid = meetingId;
        String intro = prelude != null && !prelude.isBlank()
                ? prelude + " " + buildOfflineOnlyIntroText(rt.rollCallPeople)
                : buildOfflineOnlyIntroText(rt.rollCallPeople);
        speakAsyncFutureWithDurationMs(meetingId, intro)
                .thenCompose(introDurationMs -> speakAsyncFutureWithDurationMs(meetingId, rollCallNameCue(first.name))
                        .thenAccept(cueDurationMs -> armRollCallDeadlineAfterCuePlayback(mid, introDurationMs, cueDurationMs)));
    }

    /**
     * 检点全员结束：更新 phase 为 DONE、播报总结；若当前议题标题含「检点」则 TTS 结束后自动下一议题。
     *
     * @param meetingId 会议主键
     * @param rt        主持运行时
     * @param prelude   与总结拼接的前缀短句，可为 null
     */
    private void finishRollCallDone(String meetingId, HostRuntime rt, String prelude) {
        boolean autoNextTopic = currentTopicIsRollCallChapter(rt);
        rt.rollCallPhase = "DONE";
        rt.rollCallIndex = -1;
        rt.rollCallDeadlineMs = 0;
        pushHostState(meetingId);
        String summary = buildRollCallSummary(rt.rollCallPeople);
        String prefix = prelude == null || prelude.isBlank() ? "" : prelude + " ";
        String finalMeetingId = meetingId;
        speakAsyncFutureWithDurationMs(meetingId, prefix + summary).thenAccept(durationMs -> {
            if (!autoNextTopic) {
                return;
            }
            long waitMs = Math.max(0L, durationMs) + HOST_TTS_CLIENT_PLAYBACK_TAIL_MS;
            scheduler.schedule(() -> {
                try {
                    nextTopic(finalMeetingId);
                } catch (Exception e) {
                    log.warn("auto nextTopic after roll-call: {}", e.getMessage());
                }
            }, waitMs, TimeUnit.MILLISECONDS);
        });
    }

    private static int findNextOfflineRollCallIndex(List<RollCallPerson> people, int startIndex) {
        for (int i = Math.max(0, startIndex); i < people.size(); i++) {
            if (people.get(i).isOfflinePending()) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 从会议加载检点应到名单：优先 int_meeting_participant 表；无记录时回退预设 participants_names。
     *
     * @param meeting 会议实体
     * @return 检点人员列表（含线上/线下模式与初始状态）
     */
    private List<RollCallPerson> loadRollCallPeopleFromMeeting(Meeting meeting) {
        LambdaQueryWrapper<Participant> q = new LambdaQueryWrapper<>();
        q.eq(Participant::getMeetingId, meeting.getId());
        List<Participant> rows = participantMapper.selectList(q);
        if (rows != null && !rows.isEmpty()) {
            List<RollCallPerson> list = new ArrayList<>();
            for (Participant row : rows) {
                RollCallPerson p = new RollCallPerson();
                p.participantId = row.getId();
                p.userId = row.getUserId();
                p.name = row.getName();
                p.attendanceMode = row.getAttendanceMode() != null
                        ? row.getAttendanceMode().toUpperCase()
                        : AttendanceMode.OFFLINE.name();
                if (AttendanceMode.ONLINE.name().equals(p.attendanceMode) && row.getCheckedInAt() != null) {
                    p.status = "ANSWERED";
                    p.checkInSource = row.getCheckInSource() != null
                            ? row.getCheckInSource()
                            : CheckInSource.AUTO_ONLINE.name();
                } else {
                    p.status = "PENDING";
                }
                list.add(p);
            }
            return list;
        }
        List<String> names = loadRollCallSnapshot(meeting);
        List<RollCallPerson> list = new ArrayList<>();
        for (String n : names) {
            RollCallPerson p = new RollCallPerson();
            p.name = n;
            p.userId = "preset:" + n;
            p.attendanceMode = AttendanceMode.OFFLINE.name();
            p.status = "PENDING";
            list.add(p);
        }
        return list;
    }

    private static String buildHybridRollCallIntroText(List<RollCallPerson> people) {
        long online = people.stream().filter(p -> AttendanceMode.ONLINE.name().equals(p.attendanceMode)).count();
        long offline = people.size() - online;
        return "会议检点开始。线上 " + online + " 人请打开个人入会链接确认到场，无需语音答到。"
                + (offline > 0 ? "线下 " + offline + " 人将依次答到点名。" : "");
    }

    private static String buildOfflineOnlyIntroText(List<RollCallPerson> people) {
        long offlinePending = people.stream().filter(RollCallPerson::isOfflinePending).count();
        if (offlinePending <= 0) {
            return "";
        }
        return "线下应到 " + offlinePending + " 人，请听到「请某某答到」后尽快语音答到。";
    }

    /**
     * 从会务预设读取 participants_names 解析为应到名单；仅支持 preset 1～5。
     * 与 {@link #startRollCall} 使用同一快照逻辑。
     *
     * @param meeting 当前会议，须含 presetTypeCode 1～5
     * @return 人名列表（顺序即点名顺序）
     * @throws BusinessException 类型不在 1～5、预设不存在、名单字段为空等
     */
    private List<String> loadRollCallSnapshot(Meeting meeting) {
        Integer code = meeting.getPresetTypeCode();
        if (code == null || code < 1 || code > 5) {
            throw new BusinessException(400, "会议检点需使用会务类型 1～5，以便从预设读取应到名单");
        }
        MeetingTypePreset preset = presetMapper.selectById(code);
        if (preset == null) {
            throw new BusinessException(400, "未找到会务预设: " + code);
        }
        return ParticipantNamesParser.parse(preset.getParticipantsNames());
    }

    /**
     * 检点进行中：若当前人 deadline 已到期且仍为 PENDING，记为 MISSED 并播报「某某未到」后推进。
     * 由 {@link #tick} 每秒调用；内部在锁内重新获取 runtime，避免与传入的 {@code rt} 引用不一致。
     *
     * @param meetingId 会议主键
     * @param rt          tick 开始时缓存的 runtime，仅用于快速判断 phase/index；实际逻辑以锁内最新为准
     * @param now         当前 epoch 毫秒时间戳
     */
    private void rollCallMaybeTimeout(String meetingId, HostRuntime rt, long now) {
        if (!isRollCallOfflinePhase(rt.rollCallPhase) || rt.rollCallIndex < 0) {
            return;
        }
        synchronized (lockFor(meetingId)) {
            rt = runtimes.get(meetingId);
            if (rt == null || !isRollCallOfflinePhase(rt.rollCallPhase) || rt.rollCallIndex < 0) {
                return;
            }
            if (rt.rollCallDeadlineMs <= 0 || now < rt.rollCallDeadlineMs) {
                return;
            }
            if (rt.rollCallIndex >= rt.rollCallPeople.size()) {
                return;
            }
            RollCallPerson cur = rt.rollCallPeople.get(rt.rollCallIndex);
            if (!"PENDING".equals(cur.status)) {
                return;
            }
            cur.status = "MISSED";
            advanceRollCallAfterCurrentResolved(meetingId, rt, cur.name + "未到。");
        }
    }

    /**
     * 为当前检点索引起算答到窗口截止时间（now + 单人总等待秒数）。
     * {@code deadlineMs==0} 表示本轮点名话术尚未播完，答到窗口未起算（避免倒计时被 TTS 占用）。
     *
     * @param meetingId 会议主键
     */
    private void armRollCallDeadlineIfStillActive(String meetingId) {
        synchronized (lockFor(meetingId)) {
            HostRuntime rt = runtimes.get(meetingId);
            if (rt == null || !isRollCallOfflinePhase(rt.rollCallPhase)) {
                return;
            }
            if (rt.rollCallIndex < 0 || rt.rollCallIndex >= rt.rollCallPeople.size()) {
                return;
            }
            rt.rollCallDeadlineMs = System.currentTimeMillis() + rollCallTotalWaitSec() * 1000L;
            RollCallPerson cur = rt.rollCallPeople.get(rt.rollCallIndex);
            log.info("roll-call window armed: meetingId={}, idx={}, name={}, waitSec={}",
                    meetingId, rt.rollCallIndex, cur.name, rollCallTotalWaitSec());
            pushHostState(meetingId);
        }
    }

    /**
     * 在「上一条可能仍在客户端播放的语音」全部结束后再起算答到窗口。
     *
     * @param meetingId           会议主键
     * @param priorPlaybackPadMs  紧挨在本段点名之前的、已在客户端排队的语音预估时长（毫秒），首轮为检点开场白
     * @param cueDurationMs       本段「请某某答到」等点名片断的 PCM 预估时长（毫秒）
     */
    private void armRollCallDeadlineAfterCuePlayback(String meetingId, long priorPlaybackPadMs, long cueDurationMs) {
        long delayMs = Math.max(0L, priorPlaybackPadMs) + Math.max(0L, cueDurationMs) + ROLL_CALL_ARM_EXTRA_MS + HOST_TTS_CLIENT_PLAYBACK_TAIL_MS;
        scheduler.schedule(() -> armRollCallDeadlineIfStillActive(meetingId), delayMs, TimeUnit.MILLISECONDS);
    }

    /**
     * 当前议题项是否为「会议检点」类环节（标题含「检点」且进行中），用于检点结束后自动下一议题。
     *
     * @param rt 主持运行时
     * @return 当前 RUNNING 议题标题包含「检点」时为 true
     */
    private static boolean currentTopicIsRollCallChapter(HostRuntime rt) {
        if (rt.currentIndex < 0 || rt.currentIndex >= rt.topics.size()) {
            return false;
        }
        HostTopic t = rt.topics.get(rt.currentIndex);
        return "RUNNING".equals(t.status) && t.title != null && t.title.contains("检点");
    }

    /**
     * @return 每人基础作答秒数，至少为 5，取自配置 {@code meeting.host.roll-call.window-seconds}
     */
    private int rollCallBaseWindowSec() {
        return Math.max(5, rollCallWindowSeconds);
    }

    /**
     * @return ASR 定稿缓冲秒数，取自配置且下限为 0
     */
    private int boundedAsrGraceSec() {
        return Math.max(0, hostRollCallAsrGraceSeconds);
    }

    /**
     * 单人一轮：基础作答窗口 + ASR 定稿缓冲，超时与定稿判定共用同一 deadline。
     *
     * @return 总等待秒数
     */
    private int rollCallTotalWaitSec() {
        return rollCallBaseWindowSec() + boundedAsrGraceSec();
    }

    /**
     * 检点开场：人数、答到规则与作答窗口说明（两段分支取决于是否启用 ASR 定稿缓冲秒数）。
     *
     * @param peopleCount   应到人数
     * @param baseWindowSec 每人基础作答秒（配置值，至少 5）
     * @param asrGraceSec   ASR 定稿缓冲秒（0 时话术不强调定稿延迟）
     * @return 完整开场白字符串，供 TTS 一次或分段合成
     */
    private static String buildRollCallIntroText(int peopleCount, int baseWindowSec, int asrGraceSec) {
        int total = Math.max(5, baseWindowSec) + Math.max(0, asrGraceSec);
        return "会议检点开始，应到 " + peopleCount + " 人。请听到「请某某答到」后，尽快语音答到，尽量使用到了、我在作答。下面依次点名。";
    }

    /**
     * 点名片断：「请 + 姓名 + 答到」。
     *
     * @param name 应到人姓名
     * @return 点名片断文本
     */
    private static String rollCallNameCue(String name) {
        return "请" + name + "答到。";
    }

    /**
     * 将上一人结束时的短垫话（如「收到。」）与下一人 {@link #rollCallNameCue} 拼成一段连续 TTS。
     *
     * @param prelude  上一人结果播报，可为 null 或空白
     * @param nextName 下一位应到人姓名
     * @return 拼接后的单段播报文本
     */
    private static String rollCallPreludePlusNameCue(String prelude, String nextName) {
        String cue = rollCallNameCue(nextName);
        if (prelude == null || prelude.isBlank()) {
            return cue;
        }
        return prelude.trim() + " " + cue;
    }

    /**
     * 当前检点人已处理完毕：要么进入下一位（deadline 先置 0，播完点名片断后再 {@link #armRollCallDeadlineIfStillActive}），
     * 要么全员结束（phase=DONE，播总结，若当前议题标题含「检点」则延迟后 {@link #nextTopic}）。
     *
     * @param meetingId                 会议主键
     * @param rt                        主持运行时（须在调用方已持有同 meetingId 锁的上下文中传入）
     * @param preludeForNextOrSummary   紧接下一段 TTS 前的短句，或收尾时与总结拼接的前缀
     */
    private void advanceRollCallAfterCurrentResolved(String meetingId, HostRuntime rt, String preludeForNextOrSummary) {
        int nextIdx = findNextOfflineRollCallIndex(rt.rollCallPeople, rt.rollCallIndex + 1);
        if (nextIdx < 0) {
            finishRollCallDone(meetingId, rt, preludeForNextOrSummary);
            return;
        }
        rt.rollCallIndex = nextIdx;
        rt.rollCallDeadlineMs = 0;
        pushHostState(meetingId);
        RollCallPerson next = rt.rollCallPeople.get(rt.rollCallIndex);
        final String mid = meetingId;
        String cue = rollCallPreludePlusNameCue(preludeForNextOrSummary, next.name);
        speakAsyncFutureWithDurationMs(meetingId, cue).thenAccept(cueDurationMs ->
                armRollCallDeadlineAfterCuePlayback(mid, 0L, cueDurationMs));
    }

    /**
     * 检点收尾播报：应到/实到统计，列出未到或跳过名单，或「全部到齐」。
     *
     * @param people 检点结束时的名单快照（含各人最终状态）
     * @return 供 TTS 朗读的总结字符串
     */
    private static String buildRollCallSummary(List<RollCallPerson> people) {
        int total = people.size();
        long answered = people.stream().filter(p -> "ANSWERED".equals(p.status)).count();
        List<String> absent = new ArrayList<>();
        for (RollCallPerson p : people) {
            if ("MISSED".equals(p.status) || "SKIPPED".equals(p.status)) {
                absent.add(p.name);
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("检点结束。应到 ").append(total).append(" 人，实到 ").append(answered).append(" 人。");
        if (!absent.isEmpty()) {
            sb.append("未到或跳过：").append(String.join("、", absent)).append("。");
        } else if (answered == total) {
            sb.append("全部到齐。");
        }
        return sb.toString();
    }

    /**
     * REST/轮询用：返回与主持 WS 同结构的 {@code host_state} JSON；无会话时仅 {@code active:false}。
     *
     * @param meetingId 会议主键
     * @return JSON 节点，未开始主持时为 {@code {"active":false}}
     */
    public JsonNode getStateJson(String meetingId) {
        HostRuntime rt = runtimes.get(meetingId);
        if (rt == null) {
            return objectMapper.createObjectNode().put("active", false);
        }
        return buildStateNode(rt);
    }

    /**
     * 结束主持计时与飞书静音（不停止录音；由 {@link MeetingHostMediaTeardownService} 统一编排）。
     *
     * @param meetingId 会议主键；若无活跃会话则 no-op
     */
    public void stopAndClear(String meetingId) {
        HostRuntime rt = runtimes.remove(meetingId);
        if (rt == null) {
            return;
        }
        if (rt.tick != null) {
            rt.tick.cancel(false);
        }
        runtimeLocks.remove(meetingId);
        Meeting m = meetingMapper.selectById(meetingId);
        if (m != null && m.getChatId() != null) {
            muteRegistry.unmuteChat(m.getChatId());
        } else if (rt.chatId != null) {
            muteRegistry.unmuteChat(rt.chatId);
        }
        log.info("Host session cleared: meetingId={}", meetingId);
    }

    /**
     * 解析主持议题列表：优先 POST /start 的 items；否则 preset 1～5 的 host_agenda；再本会 host_agenda；
     * 再 {@link HostAgendaConstants#DEFAULT_HOST_AGENDA_JSON}；最后硬编码保底。
     *
     * @param meeting 会议实体
     * @param body    开始请求体，可为 null
     * @return 非空议题列表
     */
    private List<HostTopic> resolveTopics(Meeting meeting, HostStartRequest body) {
        if (body != null && body.getItems() != null && !body.getItems().isEmpty()) {
            return topicsFromDtos(body.getItems());
        }
        // preset_type_code 1-5：仅按 code 读 int_meeting_type_preset.host_agenda，再回退本会 host_agenda
        Integer presetCode = meeting.getPresetTypeCode();
        if (presetCode != null && presetCode >= 1 && presetCode <= 5) {
            MeetingTypePreset preset = presetMapper.selectById(presetCode);
            if (preset != null) {
                List<HostTopic> fromPreset = topicsFromHostAgendaJson(preset.getHostAgenda());
                if (!fromPreset.isEmpty()) {
                    return fromPreset;
                }
            }
        }
        List<HostTopic> fromOwn = topicsFromHostAgendaJson(meeting.getHostAgenda());
        if (!fromOwn.isEmpty()) {
            return fromOwn;
        }
        List<HostTopic> fromDefaultJson = topicsFromHostAgendaJson(HostAgendaConstants.DEFAULT_HOST_AGENDA_JSON);
        if (!fromDefaultJson.isEmpty()) {
            return fromDefaultJson;
        }
        return defaultHostTopicsHardcoded();
    }

    /**
     * 将创建会议 API 中的 DTO 列表转为运行时 {@link HostTopic}（含可选 detail）。
     *
     * @param dtos 议程项 DTO 列表
     * @return 过滤空标题后的 {@link HostTopic} 列表（可能为空）
     */
    private List<HostTopic> topicsFromDtos(List<HostAgendaItemDto> dtos) {
        List<HostTopic> out = new ArrayList<>();
        for (HostAgendaItemDto dto : dtos) {
            if (dto.getTitle() == null || dto.getTitle().isBlank()) {
                continue;
            }
            int min = dto.getMinutes() != null && dto.getMinutes() > 0 ? dto.getMinutes() : 10;
            HostTopic t = new HostTopic();
            t.title = dto.getTitle().trim();
            t.minutes = min;
            t.status = "PENDING";
            if (dto.getDetail() != null && !dto.getDetail().isBlank()) {
                t.detail = dto.getDetail().trim();
            }
            if (dto.getFeishuDocs() != null && !dto.getFeishuDocs().isEmpty()) {
                for (FeishuDocRefDto doc : dto.getFeishuDocs()) {
                    appendDocDtoToTopic(t, doc);
                }
            } else if (dto.getFeishuDocUrl() != null && !dto.getFeishuDocUrl().isBlank()) {
                t.feishuDocUrl = dto.getFeishuDocUrl().trim();
                applyFeishuRefToHostTopic(t);
            }
            syncPrimaryUrlFromDocs(t);
            out.add(t);
        }
        return out;
    }

    /**
     * 解析 DB 中 {@code {"items":[...]}} 形态的 host_agenda JSON。
     *
     * @param hostAgendaStr JSON 字符串，可为 null 或空白
     * @return 解析出的议题列表；解析失败或空数组时返回可变的空列表
     */
    private List<HostTopic> topicsFromHostAgendaJson(String hostAgendaStr) {
        List<HostTopic> out = new ArrayList<>();
        if (hostAgendaStr == null || hostAgendaStr.isBlank()) {
            return out;
        }
        try {
            JsonNode root = objectMapper.readTree(hostAgendaStr);
            JsonNode items = root.path("items");
            if (items.isArray()) {
                for (JsonNode n : items) {
                    String title = n.path("title").asText("").trim();
                    if (title.isEmpty()) {
                        continue;
                    }
                    int min = n.path("minutes").asInt(10);
                    HostTopic t = new HostTopic();
                    t.title = title;
                    t.minutes = Math.max(1, min);
                    t.status = "PENDING";
                    String detail = n.path("detail").asText("").trim();
                    if (!detail.isEmpty()) {
                        t.detail = detail;
                    }
                    JsonNode docsArr = n.path("feishuDocs");
                    if (docsArr.isArray() && docsArr.size() > 0) {
                        for (JsonNode d : docsArr) {
                            appendDocNodeToTopic(t, d);
                        }
                    } else {
                        String docUrl = n.path("feishuDocUrl").asText("").trim();
                        if (docUrl.isEmpty()) {
                            String legacyId = n.path("feishuDocToken").asText("").trim();
                            docUrl = FeishuResourceResolver.legacyDocIdToDocxUrl(legacyId);
                            if (docUrl == null) {
                                docUrl = "";
                            }
                        }
                        if (!docUrl.isEmpty()) {
                            t.feishuDocUrl = docUrl;
                            applyFeishuRefToHostTopic(t);
                        }
                    }
                    syncPrimaryUrlFromDocs(t);
                    out.add(t);
                }
            }
        } catch (Exception e) {
            log.warn("Parse host_agenda JSON failed: {}", e.getMessage());
        }
        return out;
    }

    /** 将单条 feishuDocUrl 解析并追加到议题的资料列表。 */
    private static void applyFeishuRefToHostTopic(HostTopic t) {
        if (t == null) {
            return;
        }
        FeishuResourceRef ref = FeishuResourceResolver.resolve(t.feishuDocUrl);
        if (ref == null) {
            return;
        }
        appendResourceRefToTopic(t, ref);
        syncPrimaryUrlFromDocs(t);
    }

    /** 从 DTO 解析飞书 URL 并去重追加到议题。 */
    private static void appendDocDtoToTopic(HostTopic t, FeishuDocRefDto doc) {
        if (t == null || doc == null) {
            return;
        }
        appendResourceRefToTopic(t, FeishuResourceResolver.resolve(doc.getUrl()));
    }

    /** 从 host_agenda JSON 节点解析 URL（含 legacy token）并追加到议题。 */
    private static void appendDocNodeToTopic(HostTopic t, JsonNode d) {
        if (t == null || d == null || d.isNull()) {
            return;
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
            url = FeishuResourceResolver.legacyDocIdToDocxUrl(legacyId);
            if (url == null) {
                url = "";
            }
        }
        appendResourceRefToTopic(t, FeishuResourceResolver.resolve(url));
    }

    /** 将解析后的飞书资源引用去重追加到议题的 feishuDocs 列表。 */
    private static void appendResourceRefToTopic(HostTopic t, FeishuResourceRef ref) {
        if (t == null || ref == null || !ref.showOnHostPage()) {
            return;
        }
        if (t.feishuDocs == null) {
            t.feishuDocs = new ArrayList<>();
        }
        String openUrl = ref.defaultOpenUrl();
        FeishuDocBinding binding = new FeishuDocBinding();
        binding.kind = ref.kind().name();
        binding.url = openUrl != null ? openUrl : "";
        String key = binding.kind + "|" + binding.url;
        for (FeishuDocBinding existing : t.feishuDocs) {
            String ek = existing.kind + "|" + existing.url;
            if (ek.equals(key)) {
                return;
            }
        }
        t.feishuDocs.add(binding);
    }

    /** 同步 feishuDocUrl / feishuDocKind 为 feishuDocs 首条（兼容旧前端字段）。 */
    private static void syncPrimaryUrlFromDocs(HostTopic t) {
        if (t == null || t.feishuDocs == null || t.feishuDocs.isEmpty()) {
            return;
        }
        FeishuDocBinding first = t.feishuDocs.get(0);
        t.feishuDocKind = first.kind;
        t.feishuDocUrl = first.url;
    }

    /**
     * 将 int_matter_progress_doc_config 中 preset+agenda_index 的文档合并进运行时议题（不覆盖 JSON 已填 token/url）。
     */
    private static void sanitizeTopicsFeishuRefs(List<HostTopic> topics) {
        if (topics == null) {
            return;
        }
        for (HostTopic t : topics) {
            sanitizeTopicFeishuRefs(t);
        }
    }

    /** 过滤议题上无法识别的飞书链接，并清理空 feishuDocs。 */
    private static void sanitizeTopicFeishuRefs(HostTopic t) {
        if (t == null) {
            return;
        }
        if (t.feishuDocUrl != null && !FeishuResourceResolver.isRecognizedFeishuDocUrl(t.feishuDocUrl)) {
            t.feishuDocUrl = null;
            t.feishuDocKind = null;
        }
        if (t.feishuDocs != null && !t.feishuDocs.isEmpty()) {
            t.feishuDocs.removeIf(d -> d == null || d.url == null
                    || !FeishuResourceResolver.isRecognizedFeishuDocUrl(d.url));
            if (t.feishuDocs.isEmpty()) {
                t.feishuDocs = null;
            }
        }
        syncPrimaryUrlFromDocs(t);
    }

    /**
     * 将预设会序文档配置（int_matter_progress_doc_config）合并进运行时议题，不覆盖 JSON 已有资料。
     *
     * @param presetTypeCode 会务类型 1～5；其他值跳过
     * @param topics         运行时议题列表
     * @param meetingId      会议 ID，仅用于日志
     */
    private void mergePresetAgendaDocs(Integer presetTypeCode, List<HostTopic> topics, String meetingId) {
        if (presetTypeCode == null || presetTypeCode < 1 || presetTypeCode > 5) {
            if (meetingId != null) {
                log.debug("mergePresetAgendaDocs skipped: presetTypeCode={} meetingId={}", presetTypeCode, meetingId);
            }
            return;
        }
        if (topics == null || topics.isEmpty()) {
            return;
        }
        for (MatterProgressDocConfig cfg : presetAgendaDocService.listEnabledByPreset(presetTypeCode)) {
            if (cfg.getAgendaIndex() == null) {
                continue;
            }
            int idx = cfg.getAgendaIndex();
            if (idx < 0 || idx >= topics.size()) {
                log.warn("mergePresetAgendaDocs: agenda_index {} out of range (topics={}) preset={} config={}",
                        idx, topics.size(), presetTypeCode, cfg.getConfigName());
                continue;
            }
            HostTopic t = topics.get(idx);
            FeishuResourceRef ref = FeishuResourceResolver.resolve(cfg);
            if (ref == null) {
                continue;
            }
            appendResourceRefToTopic(t, ref);
            syncPrimaryUrlFromDocs(t);
        }
    }

    /**
     * 无任何 JSON 议程时的最后保底（与常量模板语义一致，便于本地开发）。
     *
     * @return 固定两项的议程列表
     */
    private List<HostTopic> defaultHostTopicsHardcoded() {
        HostTopic a = new HostTopic();
        a.title = "主持议题A";
        a.minutes = 3;
        a.status = "PENDING";
        a.detail = "- 开场与流程说明\n- 注意节奏与时间";
        HostTopic b = new HostTopic();
        b.title = "事项进度通报";
        b.minutes = 7;
        b.status = "PENDING";
        b.detail = "下方「事项进度通报」卡片将展示文档全文。";
        List<HostTopic> out = new ArrayList<>();
        out.add(a);
        out.add(b);
        return out;
    }

    /**
     * 将当前主持状态推送给该 meetingId 下所有主持 WS 连接。
     *
     * @param meetingId 会议主键；无 runtime 时忽略
     */
    private void pushHostState(String meetingId) {
        HostRuntime rt = runtimes.get(meetingId);
        if (rt == null) {
            return;
        }
        Meeting meeting = meetingMapper.selectById(meetingId);
        if (meeting != null) {
            sanitizeTopicsFeishuRefs(rt.topics);
            mergePresetAgendaDocs(meeting.getPresetTypeCode(), rt.topics, meetingId);
        }
        try {
            ObjectNode root = buildStateNode(rt);
            hostWebSocketHandler.broadcastText(meetingId, objectMapper.writeValueAsString(root));
        } catch (Exception e) {
            log.warn("pushHostState: {}", e.getMessage());
        }
    }

    /**
     * 构造主持端状态 JSON：{@code type=host_state}，含议题列表、当前索引、剩余毫秒、暂停、检点 phase/名单/deadline 等。
     * 字段名与 {@code host-meeting.html} 中 {@code renderHostState} 消费逻辑保持一致。
     *
     * @param rt 主持运行时（假定非 null）
     * @return 可序列化为 WebSocket 文本的 JSON 对象
     */
    private ObjectNode buildStateNode(HostRuntime rt) {
        long now = System.currentTimeMillis();
        long topicLeftMs = Math.max(0, rt.topicEndMs - now);
        long meetingLeftMs = Math.max(0, rt.meetingEndMs - now);
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "host_state");
        root.put("meetingId", rt.meetingId);
        root.put("active", true);
        root.put("paused", rt.paused);
        root.put("currentTopicIndex", rt.currentIndex);
        root.put("topicLeftMs", topicLeftMs);
        root.put("meetingLeftMs", meetingLeftMs);
        ArrayNode arr = root.putArray("topics");
        for (HostTopic t : rt.topics) {
            ObjectNode o = arr.addObject();
            o.put("title", t.title);
            o.put("minutes", t.minutes);
            o.put("status", t.status);
            o.put("detail", t.detail == null ? "" : t.detail);
            o.put("feishuDocUrl", t.feishuDocUrl == null ? "" : t.feishuDocUrl);
            o.put("feishuDocKind", t.feishuDocKind == null ? "" : t.feishuDocKind);
            ArrayNode docsArr = o.putArray("feishuDocs");
            if (t.feishuDocs != null) {
                for (FeishuDocBinding b : t.feishuDocs) {
                    ObjectNode doc = docsArr.addObject();
                    doc.put("kind", b.kind == null ? "" : b.kind);
                    doc.put("url", b.url == null ? "" : b.url);
                }
            }
        }
        ObjectNode rollCall = root.putObject("rollCall");
        rollCall.put("phase", rt.rollCallPhase);
        rollCall.put("windowSec", rollCallTotalWaitSec());
        rollCall.put("baseWindowSec", rollCallBaseWindowSec());
        rollCall.put("asrGraceSec", boundedAsrGraceSec());
        rollCall.put("onlineInventoryDeadlineMs", rt.rollCallOnlineInventoryDeadlineMs);
        rollCall.put("currentIndex", rt.rollCallIndex);
        rollCall.put("deadlineMs", rt.rollCallDeadlineMs);
        ArrayNode peopleArr = rollCall.putArray("people");
        for (RollCallPerson p : rt.rollCallPeople) {
            ObjectNode po = peopleArr.addObject();
            po.put("name", p.name);
            po.put("status", p.status);
            po.put("attendanceMode", p.attendanceMode != null ? p.attendanceMode : AttendanceMode.OFFLINE.name());
            po.put("checkInSource", p.checkInSource != null ? p.checkInSource : "");
        }
        return root;
    }

    /**
     * 主持话术播报（不返回时长）；内部仍走 {@link #speakAsyncFutureWithDurationMs}。
     *
     * @param meetingId 会议主键
     * @param text      待合成播报的完整中文话术
     */
    private void speakAsync(String meetingId, String text) {
        speakAsyncFuture(meetingId, text);
    }

    /**
     * 异步播报并返回 Future，便于检点等多段话术按顺序播放、或在结束后串联「下一议题」。
     *
     * @param meetingId 会议主键
     * @param text      待合成播报的完整中文话术
     * @return 合成与下发完成后的 Future（不关心时长毫秒）
     */
    private CompletableFuture<Void> speakAsyncFuture(String meetingId, String text) {
        return speakAsyncFutureWithDurationMs(meetingId, text).thenAccept(ms -> {
        });
    }

    /**
     * 异步合成并下发主持 TTS：在公共线程池执行讯飞合成，经 WS 发送 meta、分片 PCM、end。
     *
     * @param meetingId 会议主键，用于 WS 房间路由
     * @param text      待合成播报的完整中文话术
     * @return PCM 按 16k s16le 估算的播放时长（毫秒），供检点 deadline 与自动下一议题等链式调度使用；失败为 0
     */
    private CompletableFuture<Long> speakAsyncFutureWithDurationMs(String meetingId, String text) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                byte[] pcm = ttsSynthesizeService.synthesizeToPcm(text);
                long durationMs = estimatePcmDurationMs(pcm.length);
                String utteranceId = UUID.randomUUID().toString();
                ObjectNode meta = objectMapper.createObjectNode();
                meta.put("type", "tts_meta");
                meta.put("utteranceId", utteranceId);
                meta.put("text", text);
                if (pcm.length == 0) {
                    meta.put("encoding", "none");
                    hostWebSocketHandler.broadcastText(meetingId, objectMapper.writeValueAsString(meta));
                    broadcastTtsAudioEnd(meetingId, utteranceId);
                    return 0L;
                }
                meta.put("encoding", "pcm_s16le_16000");
                hostWebSocketHandler.broadcastText(meetingId, objectMapper.writeValueAsString(meta));
                int seq = 0;
                int offset = 0;
                // 单条 WS 文本不宜过大；6000 字节级切片与前端组帧约定
                int chunk = 6000;
                while (offset < pcm.length) {
                    int len = Math.min(chunk, pcm.length - offset);
                    byte[] slice = new byte[len];
                    System.arraycopy(pcm, offset, slice, 0, len);
                    offset += len;
                    ObjectNode chunkNode = objectMapper.createObjectNode();
                    chunkNode.put("type", "tts_audio_chunk");
                    chunkNode.put("utteranceId", utteranceId);
                    chunkNode.put("seq", seq++);
                    chunkNode.put("base64", Base64.getEncoder().encodeToString(slice));
                    hostWebSocketHandler.broadcastText(meetingId, objectMapper.writeValueAsString(chunkNode));
                }
                broadcastTtsAudioEnd(meetingId, utteranceId);
                return durationMs;
            } catch (Exception e) {
                log.warn("speakAsync: {}", e.getMessage());
                return 0L;
            }
        });
    }

    /**
     * 由 PCM 字节数按 16kHz 单声道 s16le 推算播放时长，与 {@link #PCM_SAMPLE_RATE} 一致。
     *
     * @param pcmBytes 裸 PCM 长度（字节）
     * @return 估算播放时长（毫秒），pcmBytes≤0 时为 0
     */
    private static long estimatePcmDurationMs(int pcmBytes) {
        if (pcmBytes <= 0) {
            return 0L;
        }
        long bytesPerSecond = (long) PCM_SAMPLE_RATE * PCM_BYTES_PER_SAMPLE;
        return (pcmBytes * 1000L) / bytesPerSecond;
    }

    /**
     * 一段 utterance 的音频分片已发完，前端据此组帧播放并驱动口型结束。
     *
     * @param meetingId   会议主键
     * @param utteranceId 与 tts_meta / chunk 相同的 UUID
     */
    private void broadcastTtsAudioEnd(String meetingId, String utteranceId) {
        try {
            ObjectNode end = objectMapper.createObjectNode();
            end.put("type", "tts_audio_end");
            end.put("utteranceId", utteranceId);
            hostWebSocketHandler.broadcastText(meetingId, objectMapper.writeValueAsString(end));
        } catch (Exception e) {
            log.warn("broadcastTtsAudioEnd: {}", e.getMessage());
        }
    }

    /** 单会议主持运行时状态（仅存内存，不做持久化）。 */
    private static final class HostRuntime {
        /** 会议主键 */
        String meetingId;
        /** 飞书群 chat_id，用于静音/恢复 */
        String chatId;
        /** 议程项列表，顺序即会序 */
        List<HostTopic> topics;
        /** 当前进行项下标，与 {@code host_state.currentTopicIndex} 对应 */
        int currentIndex;
        /** 当前议题预计结束时间（epoch ms） */
        long topicEndMs;
        /** 整场会议预计结束时间（epoch ms） */
        long meetingEndMs;
        boolean paused;
        /** 进入暂停的时刻，用于 resume 时回补剩余时间 */
        long pauseStartedAtMs;
        /** 与 tick 配合，避免同一阈值重复播报 */
        int lastTopicLeftSec = Integer.MAX_VALUE;
        int lastMeetingLeftSec = Integer.MAX_VALUE;
        /** 本议题是否已播过「时间到」提示（不自动切题） */
        boolean topicTimeUpAnnounced;
        /** 秒级调度句柄，stop 时 cancel */
        ScheduledFuture<?> tick;

        /** NONE / ONLINE_INVENTORY / OFFLINE_ROLL_CALL / DONE */
        String rollCallPhase = "NONE";
        final List<RollCallPerson> rollCallPeople = new ArrayList<>();
        /** 当前点到第几人；-1 表示未在逐人点名 */
        int rollCallIndex = -1;
        /** 当前人答到窗口结束时刻；0 表示尚未起算（TTS 未播完） */
        long rollCallDeadlineMs;
        /** 线上盘点阶段结束时刻 */
        long rollCallOnlineInventoryDeadlineMs;
    }

    /** 检点名单中的一人 */
    private static final class RollCallPerson {
        String participantId;
        String userId;
        /** 显示名 */
        String name;
        /** OFFLINE | ONLINE */
        String attendanceMode = AttendanceMode.OFFLINE.name();
        /** PENDING / ANSWERED / MISSED / SKIPPED */
        String status = "PENDING";
        String checkInSource;

        boolean isOnlinePending() {
            return AttendanceMode.ONLINE.name().equals(attendanceMode) && "PENDING".equals(status);
        }

        boolean isOfflinePending() {
            return AttendanceMode.OFFLINE.name().equals(attendanceMode) && "PENDING".equals(status);
        }
    }

    /** 主持议程一项（来自 JSON 或开始请求） */
    private static final class HostTopic {
        /** 会序标题 */
        String title;
        /** 预计时长（分钟） */
        int minutes;
        /** PENDING / RUNNING / COMPLETED / SKIPPED */
        String status = "PENDING";
        /** 可选 Markdown 文本，下发给主持页「当前议程」展示 */
        String detail;
        /** 首条飞书资料链接（兼容旧前端字段） */
        String feishuDocUrl;
        /** DOCX / WIKI / BASE / UNKNOWN（首条，兼容旧字段） */
        String feishuDocKind;
        /** 同一会序多条飞书资料 */
        List<FeishuDocBinding> feishuDocs;
    }

    private static final class FeishuDocBinding {
        String kind;
        String url;
    }
}
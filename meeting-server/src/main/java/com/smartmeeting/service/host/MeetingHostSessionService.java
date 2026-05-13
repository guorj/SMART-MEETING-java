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
import com.smartmeeting.entity.MeetingTypePreset;
import com.smartmeeting.exception.BusinessException;
import com.smartmeeting.repository.MeetingMapper;
import com.smartmeeting.repository.MeetingTypePresetMapper;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * AI 会议主持人：议题计时、会中飞书静音、主持 WS 推送、讯飞 TTS 播报。
 */
@Slf4j
@Service
public class MeetingHostSessionService {
    private static final int PCM_SAMPLE_RATE = 16000;
    private static final int PCM_BYTES_PER_SAMPLE = 2;
    private static final long ROLL_CALL_ARM_EXTRA_MS = 300L;
    /** 两段主持 TTS 之间须留出的客户端播放尾量（下一段 tts_meta 会触发前端 stopPlayback，过短会截断上一段） */
    private static final long HOST_TTS_CLIENT_PLAYBACK_TAIL_MS = 600L;

    private final MeetingMapper meetingMapper;
    private final MeetingTypePresetMapper presetMapper;
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

    private final Map<String, Object> runtimeLocks = new ConcurrentHashMap<>();

    private final ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(2, r -> {
        Thread t = new Thread(r, "meeting-host-tick");
        t.setDaemon(true);
        return t;
    });

    private final Map<String, HostRuntime> runtimes = new ConcurrentHashMap<>();

    public MeetingHostSessionService(MeetingMapper meetingMapper,
                                     MeetingTypePresetMapper presetMapper,
                                     MeetingHostFeishuMuteRegistry muteRegistry,
                                     @Lazy MeetingHostWebSocketHandler hostWebSocketHandler,
                                     XfyunOnlineTtsSynthesizeService ttsSynthesizeService,
                                     ObjectMapper objectMapper) {
        this.meetingMapper = meetingMapper;
        this.presetMapper = presetMapper;
        this.muteRegistry = muteRegistry;
        this.hostWebSocketHandler = hostWebSocketHandler;
        this.ttsSynthesizeService = ttsSynthesizeService;
        this.objectMapper = objectMapper;
    }

    private Object lockFor(String meetingId) {
        return runtimeLocks.computeIfAbsent(meetingId, k -> new Object());
    }

    public boolean isActive(String meetingId) {
        return runtimes.containsKey(meetingId);
    }

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

    private boolean canAutoStartRollCall(Meeting meeting) {
        try {
            List<String> names = loadRollCallSnapshot(meeting);
            return names != null && !names.isEmpty();
        } catch (BusinessException ex) {
            return false;
        }
    }

    private void safeTick(String meetingId) {
        try {
            tick(meetingId);
        } catch (Exception e) {
            log.warn("Host tick error meetingId={}: {}", meetingId, e.getMessage());
        }
    }

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

        rollCallMaybeTimeout(meetingId, rt, now);

        pushHostState(meetingId);
    }

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
            if ("ACTIVE".equals(rt.rollCallPhase) && rt.rollCallDeadlineMs > 0) {
                rt.rollCallDeadlineMs += extra;
            }
        }
        rt.paused = false;
        rt.pauseStartedAtMs = 0;
        pushHostState(meetingId);
    }

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
     * 开始会议检点：从预设 participants_names 生成冻结名单，按序点名；答到仅以 ASR 定稿且匹配答到语为准（单麦、无声纹，流程信任）。
     */
    public void startRollCall(String meetingId) {
        synchronized (lockFor(meetingId)) {
            HostRuntime rt = runtimes.get(meetingId);
            if (rt == null) {
                throw new BusinessException(400, "请先开启主持会话");
            }
            if ("ACTIVE".equals(rt.rollCallPhase)) {
                throw new BusinessException(400, "会议检点进行中");
            }
            Meeting meeting = meetingMapper.selectById(meetingId);
            if (meeting == null) {
                throw new BusinessException(404, "会议不存在: " + meetingId);
            }
            List<String> names = loadRollCallSnapshot(meeting);
            if (names.isEmpty()) {
                throw new BusinessException(400, "应到名单为空：请在预设中配置与会人名单 participants_names");
            }
            rt.rollCallPeople.clear();
            for (String n : names) {
                RollCallPerson p = new RollCallPerson();
                p.name = n;
                p.status = "PENDING";
                rt.rollCallPeople.add(p);
            }
            rt.rollCallPhase = "ACTIVE";
            rt.rollCallIndex = 0;
            rt.rollCallDeadlineMs = 0;
            pushHostState(meetingId);
            RollCallPerson first = rt.rollCallPeople.get(0);
            final String mid = meetingId;
            // 先统一说明规则与人数，再单独点名；窗口按「点名话术预计播完」后再起算，避免前几位被 TTS 播放时长吃掉。
            speakAsyncFuture(meetingId, buildRollCallIntroText(rt.rollCallPeople.size(), rollCallBaseWindowSec(), boundedAsrGraceSec()))
                    .thenCompose(v -> speakAsyncFutureWithDurationMs(meetingId, rollCallNameCue(first.name)))
                    .thenAccept(cueDurationMs -> armRollCallDeadlineAfterCuePlayback(mid, cueDurationMs));
        }
    }

    /** 跳过当前待答到人员（记为跳过，进入下一位）。 */
    public void skipCurrentRollCall(String meetingId) {
        synchronized (lockFor(meetingId)) {
            HostRuntime rt = runtimes.get(meetingId);
            if (rt == null) {
                throw new BusinessException(400, "请先开启主持会话");
            }
            if (!"ACTIVE".equals(rt.rollCallPhase) || rt.rollCallIndex < 0 || rt.rollCallIndex >= rt.rollCallPeople.size()) {
                throw new BusinessException(400, "当前不在检点点名中");
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
     * ASR 转写回调（含实时 partial 与定稿）：若处于某人答到窗口内且文本像答到，则记该人为到会并进入下一位。
     * 与 {@link #isRollCallAnswerWindowArmed(String)} 条件一致，避免 TTS 播报期间误计（deadline 未起算前不接收）。
     */
    public void onRollCallFinalTranscript(String meetingId, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        synchronized (lockFor(meetingId)) {
            HostRuntime rt = runtimes.get(meetingId);
            if (rt == null || !"ACTIVE".equals(rt.rollCallPhase)) {
                return;
            }
            if (rt.rollCallIndex < 0 || rt.rollCallIndex >= rt.rollCallPeople.size()) {
                return;
            }
            if (rt.rollCallDeadlineMs <= 0) {
                return;
            }
            long now = System.currentTimeMillis();
            if (now > rt.rollCallDeadlineMs) {
                return;
            }
            RollCallPerson cur = rt.rollCallPeople.get(rt.rollCallIndex);
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
            advanceRollCallAfterCurrentResolved(meetingId, rt, "收到。");
        }
    }

    /**
     * 是否处于会议检点「当前人、答到窗口已起算且未过期」阶段；供 ASR 仅在此时对尾包做零填充上送，避免影响整场转写帧节奏。
     */
    public boolean isRollCallAnswerWindowArmed(String meetingId) {
        synchronized (lockFor(meetingId)) {
            HostRuntime rt = runtimes.get(meetingId);
            if (rt == null || !"ACTIVE".equals(rt.rollCallPhase)) {
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
            return "PENDING".equals(cur.status);
        }
    }

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

    private void rollCallMaybeTimeout(String meetingId, HostRuntime rt, long now) {
        if (!"ACTIVE".equals(rt.rollCallPhase) || rt.rollCallIndex < 0) {
            return;
        }
        synchronized (lockFor(meetingId)) {
            rt = runtimes.get(meetingId);
            if (rt == null || !"ACTIVE".equals(rt.rollCallPhase) || rt.rollCallIndex < 0) {
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

    /** deadlineMs==0 表示本轮点名话术尚未播完，答到窗口未起算（避免倒计时被 TTS 占用）。 */
    private void armRollCallDeadlineIfStillActive(String meetingId) {
        synchronized (lockFor(meetingId)) {
            HostRuntime rt = runtimes.get(meetingId);
            if (rt == null || !"ACTIVE".equals(rt.rollCallPhase)) {
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

    private void armRollCallDeadlineAfterCuePlayback(String meetingId, long cueDurationMs) {
        long delayMs = Math.max(0L, cueDurationMs) + ROLL_CALL_ARM_EXTRA_MS;
        scheduler.schedule(() -> armRollCallDeadlineIfStillActive(meetingId), delayMs, TimeUnit.MILLISECONDS);
    }

    /** 当前议题项是否为「会议检点」类环节（标题含「检点」且进行中），用于检点结束后自动下一议题。 */
    private static boolean currentTopicIsRollCallChapter(HostRuntime rt) {
        if (rt.currentIndex < 0 || rt.currentIndex >= rt.topics.size()) {
            return false;
        }
        HostTopic t = rt.topics.get(rt.currentIndex);
        return "RUNNING".equals(t.status) && t.title != null && t.title.contains("检点");
    }

    private int rollCallBaseWindowSec() {
        return Math.max(5, rollCallWindowSeconds);
    }

    private int boundedAsrGraceSec() {
        return Math.max(0, hostRollCallAsrGraceSeconds);
    }

    /** 单人一轮：基础作答窗口 + ASR 定稿缓冲，超时与定稿判定共用 */
    private int rollCallTotalWaitSec() {
        return rollCallBaseWindowSec() + boundedAsrGraceSec();
    }

    private static String buildRollCallIntroText(int peopleCount, int baseWindowSec, int asrGraceSec) {
        int total = Math.max(5, baseWindowSec) + Math.max(0, asrGraceSec);
        if (asrGraceSec > 0) {
            return "会议检点开始，应到 " + peopleCount + " 人。请轮流靠近主持麦克风，听到「请某某答到」播报结束后开始计时，请尽快用语音答到，例如答到或在。"
                    + "每人作答窗口最长 " + total + " 秒（其中约 " + asrGraceSec + " 秒用于语音识别定稿，避免已答到却漏记）。下面依次点名。";
        }
        return "会议检点开始，应到 " + peopleCount + " 人。请轮流靠近主持麦克风，听到「请某某答到」播报结束后，在 " + total
                + " 秒内用语音答到，例如答到或在。下面依次点名。";
    }

    private static String rollCallNameCue(String name) {
        return "请" + name + "答到。";
    }

    private static String rollCallPreludePlusNameCue(String prelude, String nextName) {
        String cue = rollCallNameCue(nextName);
        if (prelude == null || prelude.isBlank()) {
            return cue;
        }
        return prelude.trim() + " " + cue;
    }

    private void advanceRollCallAfterCurrentResolved(String meetingId, HostRuntime rt, String preludeForNextOrSummary) {
        int nextIdx = rt.rollCallIndex + 1;
        if (nextIdx >= rt.rollCallPeople.size()) {
            boolean autoNextTopic = currentTopicIsRollCallChapter(rt);
            rt.rollCallPhase = "DONE";
            rt.rollCallIndex = -1;
            rt.rollCallDeadlineMs = 0;
            pushHostState(meetingId);
            String summary = buildRollCallSummary(rt.rollCallPeople);
            String prefix = preludeForNextOrSummary == null || preludeForNextOrSummary.isBlank()
                    ? ""
                    : preludeForNextOrSummary + " ";
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
            return;
        }
        rt.rollCallIndex = nextIdx;
        rt.rollCallDeadlineMs = 0;
        pushHostState(meetingId);
        RollCallPerson next = rt.rollCallPeople.get(rt.rollCallIndex);
        final String mid = meetingId;
        String cue = rollCallPreludePlusNameCue(preludeForNextOrSummary, next.name);
        speakAsyncFutureWithDurationMs(meetingId, cue).thenAccept(cueDurationMs ->
                armRollCallDeadlineAfterCuePlayback(mid, cueDurationMs));
    }

    private static String buildRollCallSummary(List<RollCallPerson> people) {
        int total = people.size();
        long answered = people.stream().filter(p -> "ANSWERED".equals(p.status)).count();
        List<String> absent = new ArrayList<>();
        for (RollCallPerson p : people) {
            if ("MISSED".equals(p.status) || "SKIPPED".equals(p.status) || "PENDING".equals(p.status)) {
                if ("PENDING".equals(p.status)) {
                    continue;
                }
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

    public JsonNode getStateJson(String meetingId) {
        HostRuntime rt = runtimes.get(meetingId);
        if (rt == null) {
            return objectMapper.createObjectNode().put("active", false);
        }
        return buildStateNode(rt);
    }

    /**
     * 结束主持计时与飞书静音（不停止录音；由 {@link MeetingHostMediaTeardownService} 统一编排）。
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
            out.add(t);
        }
        return out;
    }

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
                    out.add(t);
                }
            }
        } catch (Exception e) {
            log.warn("Parse host_agenda JSON failed: {}", e.getMessage());
        }
        return out;
    }

    private List<HostTopic> defaultHostTopicsHardcoded() {
        HostTopic a = new HostTopic();
        a.title = "主持议题A";
        a.minutes = 3;
        a.status = "PENDING";
        HostTopic b = new HostTopic();
        b.title = "事项进度通报";
        b.minutes = 7;
        b.status = "PENDING";
        List<HostTopic> out = new ArrayList<>();
        out.add(a);
        out.add(b);
        return out;
    }

    private void pushHostState(String meetingId) {
        HostRuntime rt = runtimes.get(meetingId);
        if (rt == null) {
            return;
        }
        try {
            ObjectNode root = buildStateNode(rt);
            hostWebSocketHandler.broadcastText(meetingId, objectMapper.writeValueAsString(root));
        } catch (Exception e) {
            log.warn("pushHostState: {}", e.getMessage());
        }
    }

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
        }
        ObjectNode rollCall = root.putObject("rollCall");
        rollCall.put("phase", rt.rollCallPhase);
        rollCall.put("windowSec", rollCallTotalWaitSec());
        rollCall.put("baseWindowSec", rollCallBaseWindowSec());
        rollCall.put("asrGraceSec", boundedAsrGraceSec());
        rollCall.put("currentIndex", rt.rollCallIndex);
        rollCall.put("deadlineMs", rt.rollCallDeadlineMs);
        ArrayNode peopleArr = rollCall.putArray("people");
        for (RollCallPerson p : rt.rollCallPeople) {
            ObjectNode po = peopleArr.addObject();
            po.put("name", p.name);
            po.put("status", p.status);
        }
        return root;
    }

    private void speakAsync(String meetingId, String text) {
        speakAsyncFuture(meetingId, text);
    }

    /**
     * 异步播报并返回 Future，便于检点等多段话术按顺序播放、或在结束后串联「下一议题」。
     */
    private CompletableFuture<Void> speakAsyncFuture(String meetingId, String text) {
        return speakAsyncFutureWithDurationMs(meetingId, text).thenAccept(ms -> {
        });
    }

    /**
     * 异步播报并返回「预计客户端播放时长（毫秒）」；用于检点窗口按点名播报结束后起算。
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

    private static long estimatePcmDurationMs(int pcmBytes) {
        if (pcmBytes <= 0) {
            return 0L;
        }
        long bytesPerSecond = (long) PCM_SAMPLE_RATE * PCM_BYTES_PER_SAMPLE;
        return (pcmBytes * 1000L) / bytesPerSecond;
    }

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

    private static final class HostRuntime {
        String meetingId;
        String chatId;
        List<HostTopic> topics;
        int currentIndex;
        long topicEndMs;
        long meetingEndMs;
        boolean paused;
        long pauseStartedAtMs;
        int lastTopicLeftSec = Integer.MAX_VALUE;
        int lastMeetingLeftSec = Integer.MAX_VALUE;
        boolean topicTimeUpAnnounced;
        ScheduledFuture<?> tick;

        String rollCallPhase = "NONE";
        final List<RollCallPerson> rollCallPeople = new ArrayList<>();
        int rollCallIndex = -1;
        long rollCallDeadlineMs;
    }

    private static final class RollCallPerson {
        String name;
        String status = "PENDING";
    }

    private static final class HostTopic {
        String title;
        int minutes;
        String status = "PENDING";
    }
}
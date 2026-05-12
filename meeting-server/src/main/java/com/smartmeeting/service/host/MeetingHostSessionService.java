package com.smartmeeting.service.host;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartmeeting.api.dto.host.HostAgendaItemDto;
import com.smartmeeting.api.dto.host.HostStartRequest;
import com.smartmeeting.api.config.MeetingHostWebSocketHandler;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.exception.BusinessException;
import com.smartmeeting.repository.MeetingMapper;
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
 * AI 会议主持人：议程计时、会中飞书静音、主持 WS 推送、讯飞 TTS 播报。
 */
@Slf4j
@Service
public class MeetingHostSessionService {

    private final MeetingMapper meetingMapper;
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

    private final ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(2, r -> {
        Thread t = new Thread(r, "meeting-host-tick");
        t.setDaemon(true);
        return t;
    });

    private final Map<String, HostRuntime> runtimes = new ConcurrentHashMap<>();

    public MeetingHostSessionService(MeetingMapper meetingMapper,
                                     MeetingHostFeishuMuteRegistry muteRegistry,
                                     @Lazy MeetingHostWebSocketHandler hostWebSocketHandler,
                                     XfyunOnlineTtsSynthesizeService ttsSynthesizeService,
                                     ObjectMapper objectMapper) {
        this.meetingMapper = meetingMapper;
        this.muteRegistry = muteRegistry;
        this.hostWebSocketHandler = hostWebSocketHandler;
        this.ttsSynthesizeService = ttsSynthesizeService;
        this.objectMapper = objectMapper;
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
            throw new BusinessException(400, "议程为空：请在请求体中提供 items，或为会议配置 agenda JSON");
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
        speakAsync(meetingId, "会议主持已启动。当前议题：" + rt.topics.get(0).title + "，预计 " + firstMin + " 分钟。");
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
        speakAsync(meetingId, "现在进入议题：" + next.title + "，预计 " + min + " 分钟。");
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
            speakAsync(meetingId, "议程已跳过剩余项。您可点击「结束会议」。");
            pushHostState(meetingId);
            return;
        }
        HostTopic next = rt.topics.get(rt.currentIndex);
        next.status = "RUNNING";
        int min = Math.max(1, next.minutes);
        rt.topicEndMs = System.currentTimeMillis() + min * 60_000L;
        speakAsync(meetingId, "跳过当前议题。现在进入：" + next.title + "，预计 " + min + " 分钟。");
        pushHostState(meetingId);
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
        Meeting m = meetingMapper.selectById(meetingId);
        if (m != null && m.getChatId() != null) {
            muteRegistry.unmuteChat(m.getChatId());
        } else if (rt.chatId != null) {
            muteRegistry.unmuteChat(rt.chatId);
        }
        log.info("Host session cleared: meetingId={}", meetingId);
    }

    private List<HostTopic> resolveTopics(Meeting meeting, HostStartRequest body) {
        List<HostTopic> out = new ArrayList<>();
        if (body != null && body.getItems() != null && !body.getItems().isEmpty()) {
            for (HostAgendaItemDto dto : body.getItems()) {
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
        String agendaStr = meeting.getAgenda();
        if (agendaStr == null || agendaStr.isBlank()) {
            return out;
        }
        try {
            JsonNode root = objectMapper.readTree(agendaStr);
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
            log.warn("Parse meeting.agenda failed: {}", e.getMessage());
        }
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
        return root;
    }

    private void speakAsync(String meetingId, String text) {
        CompletableFuture.runAsync(() -> {
            try {
                byte[] pcm = ttsSynthesizeService.synthesizeToPcm(text);
                String utteranceId = UUID.randomUUID().toString();
                ObjectNode meta = objectMapper.createObjectNode();
                meta.put("type", "tts_meta");
                meta.put("utteranceId", utteranceId);
                meta.put("text", text);
                if (pcm.length == 0) {
                    meta.put("encoding", "none");
                    hostWebSocketHandler.broadcastText(meetingId, objectMapper.writeValueAsString(meta));
                    return;
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
            } catch (Exception e) {
                log.warn("speakAsync: {}", e.getMessage());
            }
        });
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
    }

    private static final class HostTopic {
        String title;
        int minutes;
        String status = "PENDING";
    }
}

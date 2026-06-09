package com.smartmeeting.asr;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 按 meetingId 管理讯飞实时 ASR WebSocket 连接池，限制全局并发路数。
 */
@Slf4j
@Component
public class XfyunRealtimeSessionPool {

    @Value("${meeting.asr.max-concurrent-sessions:3}")
    private int maxConcurrentSessions;

    private final ObjectProvider<XfyunRealtimeClient> clientProvider;
    private final ConcurrentHashMap<String, XfyunRealtimeClient> sessions = new ConcurrentHashMap<>();
    private Semaphore sessionPermits;

    public XfyunRealtimeSessionPool(ObjectProvider<XfyunRealtimeClient> clientProvider) {
        this.clientProvider = clientProvider;
    }

    @PostConstruct
    void init() {
        int permits = Math.max(1, maxConcurrentSessions);
        sessionPermits = new Semaphore(permits, true);
        log.info("Xfyun ASR session pool: maxConcurrentSessions={}", permits);
    }

    /**
     * 为会议建立实时 ASR 连接（已连接则直接返回 true）。
     */
    public synchronized boolean connect(String meetingId, Consumer<AsrResult> transcriptCallback) {
        XfyunRealtimeClient existing = sessions.get(meetingId);
        if (existing != null && existing.isConnected()) {
            return true;
        }
        if (existing != null) {
            releaseSession(meetingId, existing);
        }
        try {
            if (!sessionPermits.tryAcquire(15, TimeUnit.SECONDS)) {
                log.warn("ASR session pool exhausted, meetingId={}", meetingId);
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        XfyunRealtimeClient client = clientProvider.getObject();
        if (transcriptCallback != null) {
            client.setTranscriptCallback(transcriptCallback);
        }
        if (!client.connect(meetingId)) {
            sessionPermits.release();
            return false;
        }
        sessions.put(meetingId, client);
        return true;
    }

    public XfyunRealtimeClient get(String meetingId) {
        return sessions.get(meetingId);
    }

    public boolean isActiveForMeeting(String meetingId) {
        XfyunRealtimeClient client = sessions.get(meetingId);
        return client != null && client.isConnected() && meetingId.equals(client.getCurrentMeetingId());
    }

    public void end(String meetingId) {
        XfyunRealtimeClient client = sessions.get(meetingId);
        if (client != null) {
            client.end();
        }
    }

    public synchronized void disconnect(String meetingId) {
        XfyunRealtimeClient client = sessions.remove(meetingId);
        if (client != null) {
            releaseSession(meetingId, client);
        }
    }

    public void disconnectAll() {
        for (String meetingId : sessions.keySet()) {
            disconnect(meetingId);
        }
    }

    public boolean hasAnyActiveSession() {
        for (XfyunRealtimeClient client : sessions.values()) {
            if (client != null && client.isConnected()) {
                return true;
            }
        }
        return false;
    }

    private void releaseSession(String meetingId, XfyunRealtimeClient client) {
        try {
            client.disconnect();
        } catch (Exception e) {
            log.warn("ASR disconnect meetingId={}: {}", meetingId, e.getMessage());
        } finally {
            sessionPermits.release();
        }
    }
}

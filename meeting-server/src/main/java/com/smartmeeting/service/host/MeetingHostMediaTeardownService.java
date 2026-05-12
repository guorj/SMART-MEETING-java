package com.smartmeeting.service.host;

import com.smartmeeting.api.config.AudioWebSocketHandler;
import com.smartmeeting.service.AsrBridgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 「结束会议」前 teardown：停止主持会话与飞书静音、断开 ASR、关闭录音 WebSocket。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingHostMediaTeardownService {

    private final MeetingHostSessionService meetingHostSessionService;
    private final AsrBridgeService asrBridgeService;
    private final AudioWebSocketHandler audioWebSocketHandler;

    public void beforeRecordingSessionEnd(String meetingId) {
        boolean hadHost = meetingHostSessionService.isActive(meetingId);
        if (hadHost) {
            meetingHostSessionService.stopAndClear(meetingId);
        }
        if (!hadHost) {
            return;
        }
        try {
            asrBridgeService.forceDisconnectAsr(meetingId);
        } catch (Exception e) {
            log.warn("ASR force disconnect: {}", e.getMessage());
        }
        try {
            audioWebSocketHandler.closeSessionGracefully(meetingId);
        } catch (Exception e) {
            log.warn("Audio WS close: {}", e.getMessage());
        }
    }
}

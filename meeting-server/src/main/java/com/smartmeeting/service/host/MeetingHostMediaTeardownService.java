package com.smartmeeting.service.host;

import com.smartmeeting.api.config.AudioWebSocketHandler;
import com.smartmeeting.service.AsrBridgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 会议主持媒体资源回收服务。
 *
 * <p>在「结束会议」或录音会话结束前执行 teardown：若存在活跃主持会话，
 * 则停止主持状态、强制断开 ASR，并优雅关闭录音 WebSocket。
 *
 * <p>主要协作组件：
 * <ul>
 *   <li>{@link MeetingHostSessionService} — 主持会话停止与飞书静音清理</li>
 *   <li>{@link AsrBridgeService} — ASR 强制断开</li>
 *   <li>{@link AudioWebSocketHandler} — 前端录音 WebSocket 关闭</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingHostMediaTeardownService {

    private final MeetingHostSessionService meetingHostSessionService;
    private final AsrBridgeService asrBridgeService;
    private final AudioWebSocketHandler audioWebSocketHandler;

    /**
     * 录音会话结束前的媒体与主持资源清理。
     *
     * <p>仅当该会议存在活跃主持会话时，才会继续断开 ASR 与音频 WebSocket；
     * 无主持会话时仅检查后直接返回。
     *
     * @param meetingId 会议 ID
     */
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

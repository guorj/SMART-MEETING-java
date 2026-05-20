package com.smartmeeting.service.host;

import com.smartmeeting.api.config.AudioWebSocketHandler;
import com.smartmeeting.service.AsrBridgeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

/**
 * {@link MeetingHostMediaTeardownService} 单元测试：验证录音页结束前主持媒体资源的清理顺序。
 */
@ExtendWith(MockitoExtension.class)
class MeetingHostMediaTeardownServiceTest {

    @Mock
    private MeetingHostSessionService meetingHostSessionService;
    @Mock
    private AsrBridgeService asrBridgeService;
    @Mock
    private AudioWebSocketHandler audioWebSocketHandler;

    @InjectMocks
    private MeetingHostMediaTeardownService service;

    /** 无活跃主持会话时应跳过媒体 teardown。 */
    @Test
    @DisplayName("无活跃主持：不 stop、不断 ASR、不关音频 WS")
    void whenHostInactive_skipsMediaTeardown() {
        String meetingId = "m-1";
        when(meetingHostSessionService.isActive(meetingId)).thenReturn(false);

        service.beforeRecordingSessionEnd(meetingId);

        verify(meetingHostSessionService).isActive(meetingId);
        verify(meetingHostSessionService, never()).stopAndClear(anyString());
        verifyNoInteractions(asrBridgeService);
        verifyNoInteractions(audioWebSocketHandler);
    }

    /** 有活跃主持时应依次 stop、断 ASR、关音频 WebSocket。 */
    @Test
    @DisplayName("有活跃主持：先 stop，再断 ASR，再关音频 WS")
    void whenHostActive_runsStopThenAsrThenAudioWs() {
        String meetingId = "m-2";
        when(meetingHostSessionService.isActive(meetingId)).thenReturn(true);

        service.beforeRecordingSessionEnd(meetingId);

        InOrder order = inOrder(meetingHostSessionService, asrBridgeService, audioWebSocketHandler);
        order.verify(meetingHostSessionService).isActive(meetingId);
        order.verify(meetingHostSessionService).stopAndClear(meetingId);
        order.verify(asrBridgeService).forceDisconnectAsr(meetingId);
        order.verify(audioWebSocketHandler).closeSessionGracefully(meetingId);
    }

    /** ASR 断开失败时仍应尝试关闭音频 WebSocket。 */
    @Test
    @DisplayName("ASR 断开抛错时仍尝试关闭音频 WS")
    void asrFailure_stillClosesAudioWs() {
        String meetingId = "m-3";
        when(meetingHostSessionService.isActive(meetingId)).thenReturn(true);
        doThrow(new RuntimeException("asr down")).when(asrBridgeService).forceDisconnectAsr(meetingId);

        service.beforeRecordingSessionEnd(meetingId);

        verify(audioWebSocketHandler).closeSessionGracefully(meetingId);
    }
}

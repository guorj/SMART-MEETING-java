package com.smartmeeting.service;

import com.smartmeeting.api.config.AudioWebSocketHandler;
import com.smartmeeting.asr.XfyunRealtimeClient;
import com.smartmeeting.config.MeetingAsrProperties;
import com.smartmeeting.repository.TranscriptMapper;
import com.smartmeeting.service.host.MeetingHostSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.*;

/**
 * 暂停/恢复实时 ASR 配额策略相关单元测试。
 */
@ExtendWith(MockitoExtension.class)
class AsrBridgeServicePauseTest {

    @Mock
    private XfyunRealtimeClient xfyunClient;
    @Mock
    private AudioWebSocketHandler audioWebSocketHandler;
    @Mock
    private AudioCacheService audioCacheService;
    @Mock
    private TranscriptMapper transcriptMapper;
    @Mock
    private MeetingHostSessionService meetingHostSessionService;
    @Mock
    private MeetingPresetTypeResolver presetTypeResolver;

    private MeetingAsrProperties asrProperties;
    private AsrBridgeService service;

    @BeforeEach
    void setUp() {
        asrProperties = new MeetingAsrProperties();
        asrProperties.setRealtimeEnabled(true);
        service = new AsrBridgeService(
                xfyunClient,
                audioWebSocketHandler,
                audioCacheService,
                transcriptMapper,
                meetingHostSessionService,
                asrProperties,
                presetTypeResolver);
        ReflectionTestUtils.setField(service, "primaryAsr", "xfyun");
    }

    @Test
    @DisplayName("suspendRealtimeAsr：end 后 disconnect")
    void suspendRealtimeAsr_disconnects() {
        when(xfyunClient.isConnected()).thenReturn(true);
        when(xfyunClient.getCurrentMeetingId()).thenReturn("m-1");

        service.suspendRealtimeAsr("m-1");

        verify(xfyunClient).end();
        verify(xfyunClient).disconnect();
    }

    @Test
    @DisplayName("resumeRealtimeAsr：未连接时 start")
    void resumeRealtimeAsr_startsWhenInactive() {
        when(xfyunClient.isConnected()).thenReturn(false);
        when(xfyunClient.connect("m-2")).thenReturn(true);

        boolean ok = service.resumeRealtimeAsr("m-2");

        verify(xfyunClient).connect("m-2");
        org.junit.jupiter.api.Assertions.assertTrue(ok);
    }

    @Test
    @DisplayName("isAsrActiveForMeeting：匹配当前会议")
    void isAsrActiveForMeeting() {
        when(xfyunClient.isConnected()).thenReturn(true);
        when(xfyunClient.getCurrentMeetingId()).thenReturn("m-3");

        org.junit.jupiter.api.Assertions.assertTrue(service.isAsrActiveForMeeting("m-3"));
        org.junit.jupiter.api.Assertions.assertFalse(service.isAsrActiveForMeeting("other"));
    }
}

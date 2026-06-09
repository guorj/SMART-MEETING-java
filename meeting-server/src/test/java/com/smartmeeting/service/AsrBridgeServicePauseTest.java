package com.smartmeeting.service;

import com.smartmeeting.api.config.AudioWebSocketHandler;
import com.smartmeeting.asr.XfyunRealtimeSessionPool;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 暂停/恢复实时 ASR 配额策略相关单元测试。
 */
@ExtendWith(MockitoExtension.class)
class AsrBridgeServicePauseTest {

    @Mock
    private XfyunRealtimeSessionPool asrSessionPool;
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
                asrSessionPool,
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
        when(asrSessionPool.isActiveForMeeting("m-1")).thenReturn(true);

        service.suspendRealtimeAsr("m-1");

        verify(asrSessionPool).end("m-1");
        verify(asrSessionPool).disconnect("m-1");
    }

    @Test
    @DisplayName("resumeRealtimeAsr：未连接时 start")
    void resumeRealtimeAsr_startsWhenInactive() {
        when(asrSessionPool.isActiveForMeeting("m-2")).thenReturn(false);
        when(asrSessionPool.connect(eq("m-2"), any())).thenReturn(true);

        boolean ok = service.resumeRealtimeAsr("m-2");

        verify(asrSessionPool).connect(eq("m-2"), any());
        assertTrue(ok);
    }

    @Test
    @DisplayName("isAsrActiveForMeeting：匹配当前会议")
    void isAsrActiveForMeeting() {
        when(asrSessionPool.isActiveForMeeting("m-3")).thenReturn(true);
        when(asrSessionPool.isActiveForMeeting("other")).thenReturn(false);

        assertTrue(service.isAsrActiveForMeeting("m-3"));
        assertFalse(service.isAsrActiveForMeeting("other"));
    }
}

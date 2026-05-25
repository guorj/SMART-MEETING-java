package com.smartmeeting.scheduled;

import com.smartmeeting.api.config.AudioWebSocketHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

import static org.mockito.Mockito.*;

/**
 * {@link RecordingSilenceAutoPauseScheduler} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class RecordingSilenceAutoPauseSchedulerTest {

    @Mock
    private AudioWebSocketHandler audioWebSocketHandler;

    private RecordingSilenceAutoPauseScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new RecordingSilenceAutoPauseScheduler(audioWebSocketHandler);
        ReflectionTestUtils.setField(scheduler, "silenceAutoPauseMin", 30);
    }

    @Test
    @DisplayName("静音超阈值时触发 autoPauseForSilence")
    void checkSilenceAutoPause_triggersPause() {
        String meetingId = "m-silence";
        when(audioWebSocketHandler.getActiveMeetingIds()).thenReturn(Set.of(meetingId));
        when(audioWebSocketHandler.isMeetingPaused(meetingId)).thenReturn(false);
        when(audioWebSocketHandler.getLastAudioFrameAtMs(meetingId))
                .thenReturn(System.currentTimeMillis() - 31L * 60_000);

        scheduler.checkSilenceAutoPause();

        verify(audioWebSocketHandler).autoPauseForSilence(meetingId);
    }

    @Test
    @DisplayName("已暂停的会议不再重复自动暂停")
    void checkSilenceAutoPause_skipsAlreadyPaused() {
        String meetingId = "m-paused";
        when(audioWebSocketHandler.getActiveMeetingIds()).thenReturn(Set.of(meetingId));
        when(audioWebSocketHandler.isMeetingPaused(meetingId)).thenReturn(true);

        scheduler.checkSilenceAutoPause();

        verify(audioWebSocketHandler, never()).autoPauseForSilence(anyString());
    }
}

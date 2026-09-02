package com.smartmeeting.outbox;

import com.smartmeeting.event.MeetingEndedEvent;
import com.smartmeeting.event.OfflineAsrRequestedEvent;
import com.smartmeeting.model.MinuteGenerateMessage;
import com.smartmeeting.model.OfflineAsrMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MeetingDomainEventOutboxListenerTest {

    @Mock
    private OutboxWriter outboxWriter;

    private MeetingDomainEventOutboxListener listener;

    @BeforeEach
    void setUp() {
        listener = new MeetingDomainEventOutboxListener(outboxWriter);
    }

    @Test
    @DisplayName("MeetingEndedEvent 写入 MINUTE_GENERATE outbox")
    void onMeetingEnded_writesMinuteGenerateOutbox() {
        MeetingEndedEvent event = new MeetingEndedEvent(
                "meet-1", "/tmp/a.pcm", List.of("f1"), "model", 100L);

        listener.onMeetingEnded(event);

        ArgumentCaptor<MinuteGenerateMessage> captor = ArgumentCaptor.forClass(MinuteGenerateMessage.class);
        verify(outboxWriter).write(
                eq("MEETING"),
                eq("meet-1"),
                eq(OutboxEventTypes.MINUTE_GENERATE),
                eq("minute-generate:meet-1:100"),
                captor.capture());
        assertThat(captor.getValue().getMeetingId()).isEqualTo("meet-1");
        assertThat(captor.getValue().getAudioPath()).isEqualTo("/tmp/a.pcm");
    }

    @Test
    @DisplayName("OfflineAsrRequestedEvent 写入 OFFLINE_ASR outbox")
    void onOfflineAsrRequested_writesOfflineAsrOutbox() {
        OfflineAsrRequestedEvent event = new OfflineAsrRequestedEvent(
                "meet-2", "/tmp/b.pcm", List.of(), null, 200L);

        listener.onOfflineAsrRequested(event);

        ArgumentCaptor<OfflineAsrMessage> captor = ArgumentCaptor.forClass(OfflineAsrMessage.class);
        verify(outboxWriter).write(
                eq("MEETING"),
                eq("meet-2"),
                eq(OutboxEventTypes.OFFLINE_ASR),
                eq("offline-asr:meet-2:200"),
                captor.capture());
        assertThat(captor.getValue().getMeetingId()).isEqualTo("meet-2");
    }

    @Test
    @DisplayName("异步路径依赖 fallbackExecution=true")
    void listeners_useFallbackExecutionForNonTransactionalPublish() throws Exception {
        assertFallbackExecution(MeetingDomainEventOutboxListener.class, "onMeetingEnded");
        assertFallbackExecution(MeetingDomainEventOutboxListener.class, "onOfflineAsrRequested");
        assertFallbackExecution(MeetingDomainEventOutboxListener.class, "onMinuteGenerated");
    }

    private static void assertFallbackExecution(Class<?> type, String methodName) throws Exception {
        Method method = type.getDeclaredMethod(methodName, eventParamType(type, methodName));
        TransactionalEventListener ann = method.getAnnotation(TransactionalEventListener.class);
        assertThat(ann).isNotNull();
        assertThat(ann.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
        assertThat(ann.fallbackExecution())
                .as("%s.%s must run outside active transactions (OfflineAsrService async path)", type.getSimpleName(), methodName)
                .isTrue();
    }

    private static Class<?> eventParamType(Class<?> type, String methodName) throws NoSuchMethodException {
        for (Method m : type.getDeclaredMethods()) {
            if (m.getName().equals(methodName) && m.getParameterCount() == 1) {
                return m.getParameterTypes()[0];
            }
        }
        throw new NoSuchMethodException(methodName);
    }
}

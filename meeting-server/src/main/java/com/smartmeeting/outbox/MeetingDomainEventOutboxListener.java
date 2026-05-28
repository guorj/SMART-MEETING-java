package com.smartmeeting.outbox;

import com.smartmeeting.event.MeetingEndedEvent;
import com.smartmeeting.event.MinuteGeneratedEvent;
import com.smartmeeting.model.MinuteGenerateMessage;
import com.smartmeeting.model.TodoExtractMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class MeetingDomainEventOutboxListener {

    private final OutboxWriter outboxWriter;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMeetingEnded(MeetingEndedEvent event) {
        MinuteGenerateMessage payload = MinuteGenerateMessage.builder()
                .meetingId(event.meetingId())
                .audioPath(event.audioPath())
                .featureIds(event.featureIds())
                .modelName(event.modelName())
                .sentAt(event.sentAt())
                .build();
        outboxWriter.write(
                "MEETING",
                event.meetingId(),
                OutboxEventTypes.MINUTE_GENERATE,
                "minute-generate:" + event.meetingId() + ":" + event.sentAt(),
                payload);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMinuteGenerated(MinuteGeneratedEvent event) {
        TodoExtractMessage payload = TodoExtractMessage.builder()
                .meetingId(event.meetingId())
                .sentAt(event.sentAt())
                .build();
        outboxWriter.write(
                "MEETING",
                event.meetingId(),
                OutboxEventTypes.TODO_EXTRACT,
                "todo-extract:" + event.meetingId() + ":" + event.sentAt(),
                payload);
    }
}

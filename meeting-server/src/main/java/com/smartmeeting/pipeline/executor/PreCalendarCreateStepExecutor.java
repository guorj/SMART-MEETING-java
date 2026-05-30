package com.smartmeeting.pipeline.executor;

import com.smartmeeting.entity.Meeting;
import com.smartmeeting.pipeline.StepExecutionContext;
import com.smartmeeting.pipeline.StepExecutionResult;
import com.smartmeeting.pipeline.StepExecutor;
import com.smartmeeting.repository.MeetingMapper;
import com.smartmeeting.service.FeishuService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PreCalendarCreateStepExecutor implements StepExecutor {

    private final FeishuService feishuService;
    private final MeetingMapper meetingMapper;
    private final PipelineExecutorSupport support;

    @Override
    public String stepType() {
        return "pre-calendar-create";
    }

    @Override
    public StepExecutionResult execute(StepExecutionContext context) {
        Meeting meeting = context.getMeeting();
        if (meeting == null) {
            return StepExecutionResult.failed("meeting missing");
        }
        String eventName = meeting.getTitle();
        String roomHint = support.text(support.parseConfig(context.getStep().getConfigJson()), "roomHint", "");
        FeishuService.CalendarCreateResult result = feishuService.createCalendarEvent(
                eventName,
                meeting.getScheduledTime(),
                roomHint,
                meeting.getChatId());
        if (result.success() && result.eventId() != null && !result.eventId().isBlank()) {
            meeting.setRoomId(result.eventId());
            meetingMapper.updateById(meeting);
            return StepExecutionResult.ok("calendar-created", "{\"eventId\":\"" + result.eventId() + "\"}");
        }
        return StepExecutionResult.failed("calendar-create-failed:" + result.message());
    }
}


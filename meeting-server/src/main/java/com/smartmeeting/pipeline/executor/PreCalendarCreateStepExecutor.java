package com.smartmeeting.pipeline.executor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.pipeline.StepExecutionContext;
import com.smartmeeting.pipeline.StepExecutionResult;
import com.smartmeeting.pipeline.StepExecutor;
import com.smartmeeting.service.MeetingCalendarSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PreCalendarCreateStepExecutor implements StepExecutor {

    private final PipelineExecutorSupport support;
    private final MeetingCalendarSyncService meetingCalendarSyncService;

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
        JsonNode cfg = support.parseConfig(context.getStep().getConfigJson());
        MeetingCalendarSyncService.SyncResult result = meetingCalendarSyncService.syncFromPipeline(meeting, cfg);
        if (!result.success()) {
            return StepExecutionResult.failed(result.action() + "-failed:" + result.message());
        }
        if ("skip".equals(result.action())) {
            ObjectNode skipped = support.newObject();
            skipped.put("action", "calendar-skipped");
            skipped.put("reason", result.message());
            if (result.eventId() != null && !result.eventId().isBlank()) {
                skipped.put("eventId", result.eventId());
            }
            return StepExecutionResult.ok("calendar-skipped:" + result.message(), skipped.toString());
        }

        ObjectNode out = support.newObject();
        out.put("action", "calendar-" + result.action());
        out.put("eventId", result.eventId());
        out.put("invitedCount", result.invitedCount());
        if (result.vcMeetingUrl() != null && !result.vcMeetingUrl().isBlank()) {
            out.put("vcMeetingUrl", result.vcMeetingUrl());
        }
        return StepExecutionResult.ok("calendar-" + result.action(), out.toString());
    }
}

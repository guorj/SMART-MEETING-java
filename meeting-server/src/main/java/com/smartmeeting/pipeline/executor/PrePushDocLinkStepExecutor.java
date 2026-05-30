package com.smartmeeting.pipeline.executor;

import com.smartmeeting.entity.Meeting;
import com.smartmeeting.entity.MeetingMinute;
import com.smartmeeting.pipeline.StepExecutionContext;
import com.smartmeeting.pipeline.StepExecutionResult;
import com.smartmeeting.pipeline.StepExecutor;
import com.smartmeeting.repository.MeetingMapper;
import com.smartmeeting.repository.MeetingMinuteMapper;
import com.smartmeeting.service.FeishuService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PrePushDocLinkStepExecutor implements StepExecutor {

    private final MeetingMapper meetingMapper;
    private final MeetingMinuteMapper meetingMinuteMapper;
    private final FeishuService feishuService;

    @Override
    public String stepType() {
        return "pre-push-doc-link";
    }

    @Override
    public StepExecutionResult execute(StepExecutionContext context) {
        Meeting meeting = context.getMeeting();
        if (meeting == null || meeting.getChatId() == null || meeting.getChatId().isBlank()) {
            return StepExecutionResult.failed("meeting/chatId missing");
        }
        String previousMeetingId = meeting.getPreviousMeetingId();
        if (previousMeetingId == null || previousMeetingId.isBlank()) {
            return StepExecutionResult.ok("no-previous-meeting", "{\"pushed\":false}");
        }
        Meeting prevMeeting = meetingMapper.selectById(previousMeetingId);
        MeetingMinute minute = meetingMinuteMapper.selectById(previousMeetingId);
        String docUrl = prevMeeting != null ? prevMeeting.getDocUrl() : null;
        if ((docUrl == null || docUrl.isBlank()) && minute != null) {
            docUrl = minute.getContentUrl();
        }
        if (docUrl == null || docUrl.isBlank()) {
            return StepExecutionResult.ok("previous-doc-not-found", "{\"pushed\":false}");
        }
        String text = "会前资料：已附上次会议纪要\n" + docUrl;
        boolean ok = feishuService.sendMessage(meeting.getChatId(), text);
        return StepExecutionResult.ok(ok ? "previous-doc-pushed" : "previous-doc-push-failed",
                "{\"pushed\":" + ok + "}");
    }
}


package com.smartmeeting.pipeline.executor;

import com.smartmeeting.entity.Meeting;
import com.smartmeeting.pipeline.StepExecutionContext;
import com.smartmeeting.pipeline.StepExecutionResult;
import com.smartmeeting.pipeline.StepExecutor;
import com.smartmeeting.repository.MeetingMapper;
import com.smartmeeting.service.OfflineCorrectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OfflineLlmCorrectionStepExecutor implements StepExecutor {

    private final OfflineCorrectionService offlineCorrectionService;
    private final MeetingMapper meetingMapper;

    @Override
    public String stepType() {
        return "post-offline-llm-correction";
    }

    @Override
    public StepExecutionResult execute(StepExecutionContext context) {
        Meeting meeting = meetingMapper.selectById(context.getMeetingId());
        if (meeting == null) {
            return StepExecutionResult.failed("meeting not found");
        }
        String audioPath = meeting.getAudioPath() != null && !meeting.getAudioPath().isBlank()
                ? meeting.getAudioPath() : meeting.getSourceAudioUrl();
        String correctedText = offlineCorrectionService.correct(context.getMeetingId(), audioPath);
        return StepExecutionResult.ok("offline-llm-correction-finished",
                "{\"correctedLength\":" + (correctedText == null ? 0 : correctedText.length()) + "}");
    }
}


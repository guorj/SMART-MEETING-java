package com.smartmeeting.pipeline.executor;

import com.smartmeeting.pipeline.StepExecutionContext;
import com.smartmeeting.pipeline.StepExecutionResult;
import com.smartmeeting.pipeline.StepExecutor;
import com.smartmeeting.service.VoiceprintService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VoiceprintIdentifyStepExecutor implements StepExecutor {

    private final VoiceprintService voiceprintService;

    @Override
    public String stepType() {
        return "post-voiceprint-identify";
    }

    @Override
    public StepExecutionResult execute(StepExecutionContext context) {
        int updated = voiceprintService.updateTranscriptSpeakers(context.getMeetingId());
        return StepExecutionResult.ok("voiceprint-identify-finished", "{\"updatedSegments\":" + updated + "}");
    }
}


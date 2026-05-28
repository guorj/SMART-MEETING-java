package com.smartmeeting.pipeline;

import com.smartmeeting.entity.PipelineStep;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class StepExecutionContext {
    String meetingId;
    String stage;
    PipelineStep step;
}

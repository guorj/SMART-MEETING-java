package com.smartmeeting.pipeline.executor;

import com.smartmeeting.pipeline.StepExecutionContext;
import com.smartmeeting.pipeline.StepExecutionResult;
import com.smartmeeting.pipeline.StepExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PresetSyncStepExecutor implements StepExecutor {
    @Override
    public String stepType() {
        return "preset-sync";
    }

    @Override
    public StepExecutionResult execute(StepExecutionContext context) {
        log.info("Pipeline preset-sync executed: meetingId={}, step={}",
                context.getMeetingId(), context.getStep().getStepCode());
        return StepExecutionResult.ok("preset-sync-ok", context.getStep().getConfigJson());
    }
}

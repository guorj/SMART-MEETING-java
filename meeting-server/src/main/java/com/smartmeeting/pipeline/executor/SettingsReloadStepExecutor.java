package com.smartmeeting.pipeline.executor;

import com.smartmeeting.config.MeetingRuntimeConfigLoader;
import com.smartmeeting.pipeline.StepExecutionContext;
import com.smartmeeting.pipeline.StepExecutionResult;
import com.smartmeeting.pipeline.StepExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SettingsReloadStepExecutor implements StepExecutor {

    private final MeetingRuntimeConfigLoader meetingRuntimeConfigLoader;

    @Override
    public String stepType() {
        return "settings-reload";
    }

    @Override
    public StepExecutionResult execute(StepExecutionContext context) {
        meetingRuntimeConfigLoader.reload();
        return StepExecutionResult.ok("settings-reload-ok", null);
    }
}

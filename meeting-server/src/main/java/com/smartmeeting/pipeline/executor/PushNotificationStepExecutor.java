package com.smartmeeting.pipeline.executor;

import com.smartmeeting.pipeline.StepExecutionContext;
import com.smartmeeting.pipeline.StepExecutionResult;
import com.smartmeeting.pipeline.StepExecutor;
import com.smartmeeting.service.FeishuService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PushNotificationStepExecutor implements StepExecutor {

    private final FeishuService feishuService;

    @Override
    public String stepType() {
        return "push-notification";
    }

    @Override
    public StepExecutionResult execute(StepExecutionContext context) {
        String text = "Pipeline step done: " + context.getStep().getStepName() + " (" + context.getMeetingId() + ")";
        return StepExecutionResult.ok(String.valueOf(feishuService.sendMessageToUserId("system", text)), null);
    }
}

package com.smartmeeting.pipeline;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class StepExecutionResult {
    boolean success;
    String message;
    String contextJson;

    public static StepExecutionResult ok(String message, String contextJson) {
        return StepExecutionResult.builder().success(true).message(message).contextJson(contextJson).build();
    }

    public static StepExecutionResult failed(String message) {
        return StepExecutionResult.builder().success(false).message(message).build();
    }
}

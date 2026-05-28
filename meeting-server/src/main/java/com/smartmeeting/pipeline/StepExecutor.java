package com.smartmeeting.pipeline;

public interface StepExecutor {
    String stepType();
    StepExecutionResult execute(StepExecutionContext context) throws Exception;
}

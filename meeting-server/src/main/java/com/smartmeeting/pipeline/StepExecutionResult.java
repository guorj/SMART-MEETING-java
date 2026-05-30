package com.smartmeeting.pipeline;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class StepExecutionResult {
    boolean success;
    String message;
    String contextJson;
    /**
     * 当步骤需要等待外部事件（如飞书卡片回调）时置为 true。
     */
    @Builder.Default
    boolean waitForCallback = false;
    /**
     * 等待回调步骤的唯一键（建议带业务前缀），用于回调时定位 execution。
     */
    String callbackKey;

    public static StepExecutionResult ok(String message, String contextJson) {
        return StepExecutionResult.builder().success(true).message(message).contextJson(contextJson).build();
    }

    public static StepExecutionResult waitForCallback(String message, String contextJson, String callbackKey) {
        return StepExecutionResult.builder()
                .success(true)
                .message(message)
                .contextJson(contextJson)
                .waitForCallback(true)
                .callbackKey(callbackKey)
                .build();
    }

    public static StepExecutionResult failed(String message) {
        return StepExecutionResult.builder().success(false).message(message).build();
    }
}

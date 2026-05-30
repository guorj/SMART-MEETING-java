package com.smartmeeting.pipeline.executor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartmeeting.entity.Meeting;
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
    private final PipelineExecutorSupport support;

    @Override
    public String stepType() {
        return "push-notification";
    }

    @Override
    public StepExecutionResult execute(StepExecutionContext context) {
        JsonNode cfg = support.parseConfig(context.getStep().getConfigJson());
        Meeting m = context.getMeeting();
        String template = support.text(cfg, "messageTemplate",
                "Pipeline step done: " + context.getStep().getStepName() + " ({meetingId})");
        String message = support.renderTemplate(template, context, null);
        String targetType = support.text(cfg, "targetType", "CHAT").toUpperCase();
        String targetId = support.text(cfg, "targetId", "");
        boolean requireCallback = support.bool(cfg, "requireCallback", false);

        boolean ok;
        switch (targetType) {
            case "USER" -> {
                String userId = targetId.isBlank() ? support.text(cfg, "userId", "") : targetId;
                ok = feishuService.sendMessageToUserId(userId, message);
            }
            case "CREATOR" -> {
                String creator = m != null ? m.getCreatorId() : "";
                ok = feishuService.sendMessageToUserId(creator, message);
            }
            default -> {
                String chatId = targetId.isBlank() && m != null ? m.getChatId() : targetId;
                ok = feishuService.sendMessage(chatId, message);
            }
        }

        ObjectNode out = support.newObject();
        out.put("pushNotificationOk", ok);
        out.put("pushTargetType", targetType);
        if (requireCallback) {
            String callbackKey = "pipeline:" + context.getMeetingId() + ":" + context.getStep().getId() + ":" + System.currentTimeMillis();
            out.put("callbackKey", callbackKey);
            return StepExecutionResult.waitForCallback("wait-callback", support.toJson(out), callbackKey);
        }
        return StepExecutionResult.ok(ok ? "push-ok" : "push-failed", support.toJson(out));
    }
}

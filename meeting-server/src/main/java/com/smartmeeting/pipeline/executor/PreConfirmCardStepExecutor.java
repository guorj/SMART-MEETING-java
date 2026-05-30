package com.smartmeeting.pipeline.executor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
public class PreConfirmCardStepExecutor implements StepExecutor {

    private final FeishuService feishuService;
    private final PipelineExecutorSupport support;

    @Override
    public String stepType() {
        return "pre-confirm-card";
    }

    @Override
    public StepExecutionResult execute(StepExecutionContext context) {
        Meeting meeting = context.getMeeting();
        if (meeting == null || meeting.getChatId() == null || meeting.getChatId().isBlank()) {
            return StepExecutionResult.failed("meeting/chatId missing");
        }
        JsonNode cfg = support.parseConfig(context.getStep().getConfigJson());
        String title = support.text(cfg, "title", "参会确认");
        String body = support.text(cfg, "body", "请在截止时间前确认是否参加会议。");
        String callbackKey = "pipeline:" + context.getMeetingId() + ":" + context.getStep().getId() + ":" + System.currentTimeMillis();
        String cardJson = buildConfirmCardJson(title, body, callbackKey);
        boolean ok = feishuService.sendInteractiveCard(meeting.getChatId(), cardJson);
        ObjectNode out = support.newObject();
        out.put("callbackKey", callbackKey);
        out.put("confirmCardSent", ok);
        return StepExecutionResult.waitForCallback("confirm-card-sent", support.toJson(out), callbackKey);
    }

    private String buildConfirmCardJson(String title, String body, String callbackKey) {
        ObjectNode card = support.newObject();
        card.putObject("config").put("wide_screen_mode", true);
        ObjectNode header = card.putObject("header");
        header.put("template", "blue");
        ObjectNode headerTitle = header.putObject("title");
        headerTitle.put("tag", "plain_text");
        headerTitle.put("content", title);
        ArrayNode elements = card.putArray("elements");
        ObjectNode div = elements.addObject();
        div.put("tag", "div");
        div.putObject("text").put("tag", "lark_md").put("content", body);
        ObjectNode action = elements.addObject();
        action.put("tag", "action");
        ArrayNode actions = action.putArray("actions");
        actions.add(button("参加", "primary", callbackKey, "confirm_attend"));
        actions.add(button("请假", "default", callbackKey, "decline_attend"));
        return card.toString();
    }

    private ObjectNode button(String text, String type, String callbackKey, String decision) {
        ObjectNode btn = support.newObject();
        btn.put("tag", "button");
        btn.put("type", type);
        btn.putObject("text").put("tag", "plain_text").put("content", text);
        ObjectNode value = btn.putObject("value");
        value.put("cmd", "pipeline-callback");
        value.put("callbackKey", callbackKey);
        value.put("decision", decision);
        return btn;
    }
}


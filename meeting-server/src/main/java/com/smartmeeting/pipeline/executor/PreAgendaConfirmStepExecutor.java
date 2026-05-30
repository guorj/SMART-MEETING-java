package com.smartmeeting.pipeline.executor;

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
public class PreAgendaConfirmStepExecutor implements StepExecutor {

    private final FeishuService feishuService;
    private final PipelineExecutorSupport support;

    @Override
    public String stepType() {
        return "pre-agenda-confirm";
    }

    @Override
    public StepExecutionResult execute(StepExecutionContext context) {
        Meeting m = context.getMeeting();
        if (m == null || m.getChatId() == null || m.getChatId().isBlank()) {
            return StepExecutionResult.failed("meeting/chatId missing");
        }
        String callbackKey = "pipeline:" + context.getMeetingId() + ":" + context.getStep().getId() + ":" + System.currentTimeMillis();
        String cardJson = buildAgendaCard(m, callbackKey);
        boolean ok = feishuService.sendInteractiveCard(m.getChatId(), cardJson);
        ObjectNode out = support.newObject();
        out.put("callbackKey", callbackKey);
        out.put("agendaConfirmSent", ok);
        return StepExecutionResult.waitForCallback("agenda-confirm-wait", support.toJson(out), callbackKey);
    }

    private String buildAgendaCard(Meeting meeting, String callbackKey) {
        ObjectNode card = support.newObject();
        card.putObject("config").put("wide_screen_mode", true);
        ObjectNode header = card.putObject("header");
        header.put("template", "wathet");
        header.putObject("title").put("tag", "plain_text").put("content", "议题确认");
        ArrayNode elements = card.putArray("elements");
        elements.addObject().put("tag", "div").putObject("text").put("tag", "lark_md")
                .put("content", "会议：" + meeting.getTitle() + "\n请确认本次议题是否可直接发送邀约。");
        ObjectNode action = elements.addObject();
        action.put("tag", "action");
        ArrayNode actions = action.putArray("actions");
        actions.add(button("确认议程", "primary", callbackKey, "agenda_confirm"));
        actions.add(button("需要调整", "default", callbackKey, "agenda_revise"));
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


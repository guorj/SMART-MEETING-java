package com.smartmeeting.pipeline.executor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.entity.Participant;
import com.smartmeeting.pipeline.StepExecutionContext;
import com.smartmeeting.pipeline.StepExecutionResult;
import com.smartmeeting.pipeline.StepExecutor;
import com.smartmeeting.repository.ParticipantMapper;
import com.smartmeeting.service.FeishuService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class PreKeyDeclinerAlertStepExecutor implements StepExecutor {

    private final ParticipantMapper participantMapper;
    private final FeishuService feishuService;
    private final PipelineExecutorSupport support;

    @Override
    public String stepType() {
        return "pre-key-decliner-alert";
    }

    @Override
    public StepExecutionResult execute(StepExecutionContext context) {
        Meeting meeting = context.getMeeting();
        if (meeting == null || meeting.getCreatorId() == null || meeting.getCreatorId().isBlank()) {
            return StepExecutionResult.failed("meeting/creator missing");
        }
        JsonNode cfg = support.parseConfig(context.getStep().getConfigJson());
        Set<String> keyUserIds = parseKeyUserIds(cfg.path("keyUserIds"));
        if (keyUserIds.isEmpty()) {
            return StepExecutionResult.ok("no-key-users-config", "{\"alerted\":0}");
        }
        List<Participant> declined = participantMapper.selectList(new LambdaQueryWrapper<Participant>()
                .eq(Participant::getMeetingId, context.getMeetingId())
                .eq(Participant::getStatus, "DECLINED"));
        long keyDeclined = declined.stream().filter(p -> keyUserIds.contains(p.getUserId())).count();
        if (keyDeclined <= 0) {
            return StepExecutionResult.ok("no-key-declined", "{\"alerted\":0}");
        }
        feishuService.sendMessageToUserId(meeting.getCreatorId(),
                "关键参会人请假提醒：会议「" + meeting.getTitle() + "」有 " + keyDeclined + " 位关键参会人已请假，请确认是否延期。");
        return StepExecutionResult.ok("key-decliner-alerted", "{\"alerted\":" + keyDeclined + "}");
    }

    private Set<String> parseKeyUserIds(JsonNode arrayNode) {
        Set<String> out = new HashSet<>();
        if (arrayNode != null && arrayNode.isArray()) {
            for (JsonNode n : arrayNode) {
                String s = n.asText("").trim();
                if (!s.isBlank()) {
                    out.add(s);
                }
            }
        }
        return out;
    }
}


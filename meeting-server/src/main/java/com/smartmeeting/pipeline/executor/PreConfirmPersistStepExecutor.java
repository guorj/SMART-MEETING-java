package com.smartmeeting.pipeline.executor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.smartmeeting.entity.Participant;
import com.smartmeeting.pipeline.StepExecutionContext;
import com.smartmeeting.pipeline.StepExecutionResult;
import com.smartmeeting.pipeline.StepExecutor;
import com.smartmeeting.repository.ParticipantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PreConfirmPersistStepExecutor implements StepExecutor {

    private final ParticipantMapper participantMapper;
    private final PipelineExecutorSupport support;

    @Override
    public String stepType() {
        return "pre-confirm-persist";
    }

    @Override
    public StepExecutionResult execute(StepExecutionContext context) {
        JsonNode shared = support.parseConfig(context.getSharedContextJson());
        String operator = shared.path("callbackOperator").asText("");
        String decision = shared.path("callbackPayload").path("decision").asText("");
        if (operator.isBlank() || decision.isBlank()) {
            return StepExecutionResult.ok("no-callback-decision", "{\"updated\":0}");
        }
        Participant p = participantMapper.selectOne(new LambdaQueryWrapper<Participant>()
                .eq(Participant::getMeetingId, context.getMeetingId())
                .eq(Participant::getUserId, operator)
                .last("LIMIT 1"));
        if (p == null) {
            return StepExecutionResult.ok("participant-not-found", "{\"updated\":0}");
        }
        if ("confirm_attend".equalsIgnoreCase(decision)) {
            p.setStatus("CONFIRMED");
        } else if ("decline_attend".equalsIgnoreCase(decision)) {
            p.setStatus("DECLINED");
        } else {
            return StepExecutionResult.ok("decision-ignored", "{\"updated\":0}");
        }
        participantMapper.updateById(p);
        return StepExecutionResult.ok("confirm-persist-ok", "{\"updated\":1,\"status\":\"" + p.getStatus() + "\"}");
    }
}


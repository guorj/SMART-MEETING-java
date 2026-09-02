package com.smartmeeting.pipeline;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmeeting.config.MeetingPipelineProperties;
import com.smartmeeting.entity.PipelineStepExecution;
import com.smartmeeting.event.MeetingEndedEvent;
import com.smartmeeting.repository.PipelineStepExecutionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 会议结束后自动触发 POST Pipeline（不影响 MID）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PipelinePostStageAutoTriggerListener {

    private final PipelineStepDispatcher dispatcher;
    private final PipelineStepExecutionMapper executionMapper;
    private final MeetingPipelineProperties pipelineProperties;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onMeetingEnded(MeetingEndedEvent event) {
        if (!pipelineProperties.getPostAutoTrigger().isEnabled() || event == null || event.meetingId() == null || event.meetingId().isBlank()) {
            return;
        }
        String meetingId = event.meetingId();
        if (alreadyTriggeredPost(meetingId)) {
            return;
        }
        String templateCode = safeTrim(pipelineProperties.getPostAutoTrigger().getTemplateCode());
        try {
            int created = dispatcher.createExecutionPlan(meetingId, "POST", templateCode);
            if (created > 0) {
                dispatcher.dispatchPending(meetingId, "POST");
                log.info("Pipeline POST auto triggered: meetingId={}, templateCode={}, created={}",
                        meetingId, templateCode, created);
            }
        } catch (Exception e) {
            log.warn("Pipeline POST auto trigger skipped: meetingId={}, templateCode={}, err={}",
                    meetingId, templateCode, e.getMessage());
        }
    }

    private boolean alreadyTriggeredPost(String meetingId) {
        Long count = executionMapper.selectCount(new LambdaQueryWrapper<PipelineStepExecution>()
                .eq(PipelineStepExecution::getMeetingId, meetingId)
                .eq(PipelineStepExecution::getStage, "POST"));
        return count != null && count > 0;
    }

    private String safeTrim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}


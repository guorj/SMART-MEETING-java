package com.smartmeeting.scheduled;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmeeting.config.MeetingSchedulerProperties;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.entity.PipelineStepExecution;
import com.smartmeeting.entity.PipelineTemplate;
import com.smartmeeting.pipeline.PipelineStepDispatcher;
import com.smartmeeting.repository.MeetingMapper;
import com.smartmeeting.repository.PipelineStepExecutionMapper;
import com.smartmeeting.repository.PipelineTemplateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会议触发器：按计划时间窗口自动触发 PRE 流水线。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MeetingScheduler {

    private final MeetingMapper meetingMapper;
    private final PipelineStepExecutionMapper executionMapper;
    private final PipelineTemplateMapper templateMapper;
    private final PipelineStepDispatcher dispatcher;
    private final MeetingSchedulerProperties schedulerProperties;

    @Scheduled(fixedDelayString = "${meeting.scheduler.scan-ms:300000}")
    public void triggerPreStage() {
        if (!schedulerProperties.isPreEnabled()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        int window = Math.max(1, schedulerProperties.getPreWindowMinutes());
        LocalDateTime upper = now.plusHours(24).plusMinutes(window);
        List<Meeting> meetings = meetingMapper.selectList(new LambdaQueryWrapper<Meeting>()
                .isNotNull(Meeting::getScheduledTime)
                .between(Meeting::getScheduledTime, now, upper)
                .in(Meeting::getStatus, List.of("ISSUE_COLLECTING", "INVITED", "STARTED"))
                .last("LIMIT 200"));
        for (Meeting meeting : meetings) {
            // 兼容旧配置：未提供双时点模板时，沿用“会前 24h 单次触发 PRE”
            if ((schedulerProperties.getPre24hTemplateCode() == null || schedulerProperties.getPre24hTemplateCode().isBlank())
                    && (schedulerProperties.getPre10mTemplateCode() == null || schedulerProperties.getPre10mTemplateCode().isBlank())) {
                if (!alreadyTriggeredPre(meeting.getId())) {
                    triggerPreTemplate(meeting.getId(), null);
                }
                continue;
            }
            if (shouldTriggerAt(now, meeting.getScheduledTime().minusHours(24), window)) {
                triggerPreTemplate(meeting.getId(), safeTrim(schedulerProperties.getPre24hTemplateCode()));
            }
            if (shouldTriggerAt(now, meeting.getScheduledTime().minusMinutes(10), window)) {
                triggerPreTemplate(meeting.getId(), safeTrim(schedulerProperties.getPre10mTemplateCode()));
            }
        }
    }

    private void triggerPreTemplate(String meetingId, String templateCode) {
        try {
            if (templateCode != null && !templateCode.isBlank()) {
                PipelineTemplate template = templateMapper.selectOne(new LambdaQueryWrapper<PipelineTemplate>()
                        .eq(PipelineTemplate::getStage, "PRE")
                        .eq(PipelineTemplate::getTemplateCode, templateCode)
                        .eq(PipelineTemplate::getEnabled, 1)
                        .last("LIMIT 1"));
                if (template == null) {
                    log.warn("MeetingScheduler skip PRE trigger: template not found, meetingId={}, templateCode={}",
                            meetingId, templateCode);
                    return;
                }
                if (alreadyTriggeredPreTemplate(meetingId, template.getId())) {
                    return;
                }
            } else if (alreadyTriggeredPre(meetingId)) {
                return;
            }
            int created = dispatcher.createExecutionPlan(meetingId, "PRE", templateCode);
            if (created > 0) {
                dispatcher.dispatchPending(meetingId, "PRE");
                log.info("MeetingScheduler triggered PRE pipeline: meetingId={}, templateCode={}, created={}",
                        meetingId, templateCode, created);
            }
        } catch (Exception e) {
            log.warn("MeetingScheduler trigger PRE failed: meetingId={}, templateCode={}, err={}",
                    meetingId, templateCode, e.getMessage());
        }
    }

    private boolean alreadyTriggeredPre(String meetingId) {
        Long count = executionMapper.selectCount(new LambdaQueryWrapper<PipelineStepExecution>()
                .eq(PipelineStepExecution::getMeetingId, meetingId)
                .eq(PipelineStepExecution::getStage, "PRE"));
        return count != null && count > 0;
    }

    private boolean alreadyTriggeredPreTemplate(String meetingId, Long templateId) {
        Long count = executionMapper.selectCount(new LambdaQueryWrapper<PipelineStepExecution>()
                .eq(PipelineStepExecution::getMeetingId, meetingId)
                .eq(PipelineStepExecution::getStage, "PRE")
                .eq(PipelineStepExecution::getTemplateId, templateId));
        return count != null && count > 0;
    }

    private boolean shouldTriggerAt(LocalDateTime now, LocalDateTime targetTime, int windowMinutes) {
        if (targetTime == null) {
            return false;
        }
        return !now.isBefore(targetTime.minusMinutes(windowMinutes))
                && !now.isAfter(targetTime.plusMinutes(windowMinutes));
    }

    private String safeTrim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}


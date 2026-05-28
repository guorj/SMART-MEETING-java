package com.smartmeeting.pipeline;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmeeting.entity.PipelineStep;
import com.smartmeeting.entity.PipelineStepExecution;
import com.smartmeeting.entity.PipelineTemplate;
import com.smartmeeting.exception.BusinessException;
import com.smartmeeting.repository.PipelineStepExecutionMapper;
import com.smartmeeting.repository.PipelineStepMapper;
import com.smartmeeting.repository.PipelineTemplateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PipelineStepDispatcher {

    private final PipelineTemplateMapper templateMapper;
    private final PipelineStepMapper stepMapper;
    private final PipelineStepExecutionMapper executionMapper;
    private final List<StepExecutor> executors;

    @Transactional
    public int createExecutionPlan(String meetingId, String stage, String templateCode) {
        PipelineTemplate template = loadTemplate(stage, templateCode);
        List<PipelineStep> steps = loadEnabledSteps(template.getId());
        for (PipelineStep step : steps) {
            PipelineStepExecution exec = new PipelineStepExecution();
            exec.setMeetingId(meetingId);
            exec.setTemplateId(template.getId());
            exec.setStepId(step.getId());
            exec.setStage(stage);
            exec.setStatus("PENDING");
            exec.setRetryCount(0);
            exec.setMaxRetries(3);
            if (step.getTimeoutSeconds() != null && step.getTimeoutSeconds() > 0) {
                exec.setTimeoutAt(LocalDateTime.now().plusSeconds(step.getTimeoutSeconds()));
            }
            executionMapper.insert(exec);
        }
        return steps.size();
    }

    @Transactional
    public void dispatchPending(String meetingId, String stage) {
        LambdaQueryWrapper<PipelineStepExecution> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PipelineStepExecution::getMeetingId, meetingId)
                .eq(PipelineStepExecution::getStage, stage)
                .in(PipelineStepExecution::getStatus, List.of("PENDING", "RETRY"))
                .orderByAsc(PipelineStepExecution::getId);
        List<PipelineStepExecution> pending = executionMapper.selectList(wrapper);
        Map<Long, PipelineStep> stepMap = stepMapper.selectBatchIds(
                pending.stream().map(PipelineStepExecution::getStepId).distinct().toList())
                .stream().collect(Collectors.toMap(PipelineStep::getId, s -> s));

        for (PipelineStepExecution exec : pending) {
            PipelineStep step = stepMap.get(exec.getStepId());
            if (step == null || step.getEnabled() == null || step.getEnabled() != 1) {
                continue;
            }
            StepExecutor executor = resolveExecutor(step.getStepType());
            if (executor == null) {
                markFailed(exec, "No executor for stepType=" + step.getStepType());
                continue;
            }
            exec.setStatus("RUNNING");
            exec.setStartedAt(LocalDateTime.now());
            executionMapper.updateById(exec);

            try {
                StepExecutionResult result = executor.execute(StepExecutionContext.builder()
                        .meetingId(meetingId)
                        .stage(stage)
                        .step(step)
                        .build());
                if (result.isSuccess()) {
                    exec.setStatus("SUCCESS");
                    exec.setContextJson(result.getContextJson());
                    exec.setLastError(null);
                } else {
                    markRetryOrFailed(exec, result.getMessage());
                    continue;
                }
            } catch (Exception e) {
                markRetryOrFailed(exec, e.getMessage());
                continue;
            }
            exec.setEndedAt(LocalDateTime.now());
            executionMapper.updateById(exec);
        }
    }

    @Transactional
    public int markTimeoutExecutions() {
        LambdaQueryWrapper<PipelineStepExecution> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PipelineStepExecution::getStatus, "WAITING")
                .le(PipelineStepExecution::getTimeoutAt, LocalDateTime.now());
        List<PipelineStepExecution> timeoutRows = executionMapper.selectList(wrapper);
        for (PipelineStepExecution row : timeoutRows) {
            row.setStatus("TIMEOUT");
            row.setEndedAt(LocalDateTime.now());
            row.setLastError("timeout");
            executionMapper.updateById(row);
        }
        return timeoutRows.size();
    }

    private PipelineTemplate loadTemplate(String stage, String templateCode) {
        LambdaQueryWrapper<PipelineTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PipelineTemplate::getStage, stage)
                .eq(PipelineTemplate::getEnabled, 1);
        if (templateCode != null && !templateCode.isBlank()) {
            wrapper.eq(PipelineTemplate::getTemplateCode, templateCode);
        }
        wrapper.last("LIMIT 1");
        PipelineTemplate template = templateMapper.selectOne(wrapper);
        if (template == null) {
            throw new BusinessException("未找到可用 pipeline 模板: stage=" + stage);
        }
        return template;
    }

    private List<PipelineStep> loadEnabledSteps(Long templateId) {
        LambdaQueryWrapper<PipelineStep> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PipelineStep::getTemplateId, templateId)
                .eq(PipelineStep::getEnabled, 1);
        return stepMapper.selectList(wrapper).stream()
                .sorted(Comparator.comparingInt(PipelineStep::getOrderNo))
                .toList();
    }

    private StepExecutor resolveExecutor(String stepType) {
        return executors.stream()
                .filter(e -> e.stepType().equalsIgnoreCase(stepType))
                .findFirst()
                .orElse(null);
    }

    private void markRetryOrFailed(PipelineStepExecution exec, String message) {
        int retry = exec.getRetryCount() == null ? 0 : exec.getRetryCount();
        int maxRetries = exec.getMaxRetries() == null ? 3 : exec.getMaxRetries();
        exec.setRetryCount(retry + 1);
        exec.setLastError(shortMessage(message));
        exec.setEndedAt(LocalDateTime.now());
        if (retry + 1 >= maxRetries) {
            exec.setStatus("FAILED");
        } else {
            exec.setStatus("RETRY");
        }
        executionMapper.updateById(exec);
    }

    private void markFailed(PipelineStepExecution exec, String message) {
        exec.setStatus("FAILED");
        exec.setLastError(shortMessage(message));
        exec.setEndedAt(LocalDateTime.now());
        executionMapper.updateById(exec);
    }

    private String shortMessage(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}

package com.smartmeeting.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmeeting.admin.api.dto.PipelineExecutionDto;
import com.smartmeeting.admin.api.dto.PipelineStepDto;
import com.smartmeeting.admin.api.dto.PipelineTemplateDto;
import com.smartmeeting.admin.entity.PipelineStep;
import com.smartmeeting.admin.entity.PipelineStepExecution;
import com.smartmeeting.admin.entity.PipelineTemplate;
import com.smartmeeting.admin.exception.BusinessException;
import com.smartmeeting.admin.repository.PipelineStepExecutionMapper;
import com.smartmeeting.admin.repository.PipelineStepMapper;
import com.smartmeeting.admin.repository.PipelineTemplateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PipelineAdminService {

    private final PipelineTemplateMapper templateMapper;
    private final PipelineStepMapper stepMapper;
    private final PipelineStepExecutionMapper executionMapper;
    private final MeetingServerBridgeService meetingServerBridgeService;

    public List<PipelineTemplateDto> listTemplates() {
        return templateMapper.selectList(new LambdaQueryWrapper<PipelineTemplate>()
                        .orderByAsc(PipelineTemplate::getStage)
                        .orderByAsc(PipelineTemplate::getId))
                .stream().map(this::toTemplateDto).toList();
    }

    @Transactional
    public long saveTemplate(PipelineTemplateDto dto) {
        PipelineTemplate row = new PipelineTemplate();
        row.setTemplateCode(dto.getTemplateCode());
        row.setTemplateName(dto.getTemplateName());
        row.setStage(dto.getStage());
        row.setEnabled(dto.isEnabled() ? 1 : 0);
        row.setVersionNo(dto.getVersionNo() == null ? 1 : dto.getVersionNo());
        row.setDescription(dto.getDescription());
        if (dto.getId() == null) {
            templateMapper.insert(row);
        } else {
            row.setId(dto.getId());
            templateMapper.updateById(row);
        }
        return row.getId();
    }

    public List<PipelineStepDto> listSteps(long templateId) {
        return stepMapper.selectList(new LambdaQueryWrapper<PipelineStep>()
                        .eq(PipelineStep::getTemplateId, templateId)
                        .orderByAsc(PipelineStep::getOrderNo)
                        .orderByAsc(PipelineStep::getId))
                .stream().map(this::toStepDto).toList();
    }

    @Transactional
    public long saveStep(PipelineStepDto dto) {
        if (dto.getTemplateId() == null) {
            throw new BusinessException("templateId 必填");
        }
        PipelineStep row = new PipelineStep();
        row.setTemplateId(dto.getTemplateId());
        row.setStepCode(dto.getStepCode());
        row.setStepName(dto.getStepName());
        row.setStepType(dto.getStepType());
        row.setStage(dto.getStage());
        row.setOrderNo(dto.getOrderNo() == null ? 1 : dto.getOrderNo());
        row.setTimeoutSeconds(dto.getTimeoutSeconds());
        row.setConfigJson(dto.getConfigJson());
        row.setEnabled(dto.isEnabled() ? 1 : 0);
        if (dto.getId() == null) {
            stepMapper.insert(row);
        } else {
            row.setId(dto.getId());
            stepMapper.updateById(row);
        }
        return row.getId();
    }

    @Transactional
    public void deleteTemplate(long templateId) {
        PipelineTemplate existing = templateMapper.selectById(templateId);
        if (existing == null) {
            throw new BusinessException("template 不存在: " + templateId);
        }
        stepMapper.delete(new LambdaQueryWrapper<PipelineStep>()
                .eq(PipelineStep::getTemplateId, templateId));
        templateMapper.deleteById(templateId);
    }

    @Transactional
    public void deleteStep(long stepId) {
        PipelineStep existing = stepMapper.selectById(stepId);
        if (existing == null) {
            throw new BusinessException("step 不存在: " + stepId);
        }
        stepMapper.deleteById(stepId);
    }

    public List<PipelineExecutionDto> listExecutions(String meetingId) {
        return executionMapper.selectList(new LambdaQueryWrapper<PipelineStepExecution>()
                        .eq(meetingId != null && !meetingId.isBlank(), PipelineStepExecution::getMeetingId, meetingId)
                        .orderByDesc(PipelineStepExecution::getId)
                        .last("LIMIT 200"))
                .stream().map(this::toExecutionDto).toList();
    }

    public Map<String, Object> executePipeline(String meetingId, Integer presetTypeCode, String stage, String templateCode, Boolean skipExisting) {
        String finalStage = (stage == null || stage.isBlank()) ? "PRE" : stage.trim().toUpperCase();
        String finalTemplate = (templateCode == null || templateCode.isBlank()) ? null : templateCode.trim();
        String mid = meetingId == null ? null : meetingId.trim();
        if (mid != null && !mid.isBlank()) {
            meetingServerBridgeService.executePipeline(mid, finalStage, finalTemplate);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("mode", "meeting");
            out.put("meetingId", mid);
            out.put("stage", finalStage);
            out.put("templateCode", finalTemplate == null ? "" : finalTemplate);
            return out;
        }
        if (presetTypeCode == null || presetTypeCode <= 0) {
            throw new BusinessException("meetingId 或 presetTypeCode 至少提供一个");
        }
        boolean skip = skipExisting == null || skipExisting;
        Map<String, Object> out = meetingServerBridgeService.executePipelineByPreset(
                presetTypeCode, finalStage, finalTemplate, skip);
        out.put("mode", "preset");
        return out;
    }

    private PipelineTemplateDto toTemplateDto(PipelineTemplate row) {
        PipelineTemplateDto dto = new PipelineTemplateDto();
        dto.setId(row.getId());
        dto.setTemplateCode(row.getTemplateCode());
        dto.setTemplateName(row.getTemplateName());
        dto.setStage(row.getStage());
        dto.setEnabled(row.getEnabled() != null && row.getEnabled() == 1);
        dto.setVersionNo(row.getVersionNo());
        dto.setDescription(row.getDescription());
        return dto;
    }

    private PipelineStepDto toStepDto(PipelineStep row) {
        PipelineStepDto dto = new PipelineStepDto();
        dto.setId(row.getId());
        dto.setTemplateId(row.getTemplateId());
        dto.setStepCode(row.getStepCode());
        dto.setStepName(row.getStepName());
        dto.setStepType(row.getStepType());
        dto.setStage(row.getStage());
        dto.setOrderNo(row.getOrderNo());
        dto.setTimeoutSeconds(row.getTimeoutSeconds());
        dto.setConfigJson(row.getConfigJson());
        dto.setEnabled(row.getEnabled() != null && row.getEnabled() == 1);
        return dto;
    }

    private PipelineExecutionDto toExecutionDto(PipelineStepExecution row) {
        PipelineExecutionDto dto = new PipelineExecutionDto();
        dto.setId(row.getId());
        dto.setMeetingId(row.getMeetingId());
        dto.setTemplateId(row.getTemplateId());
        dto.setStepId(row.getStepId());
        dto.setStage(row.getStage());
        dto.setStatus(row.getStatus());
        dto.setRetryCount(row.getRetryCount());
        dto.setMaxRetries(row.getMaxRetries());
        dto.setTimeoutAt(row.getTimeoutAt());
        dto.setStartedAt(row.getStartedAt());
        dto.setEndedAt(row.getEndedAt());
        dto.setLastError(row.getLastError());
        return dto;
    }
}

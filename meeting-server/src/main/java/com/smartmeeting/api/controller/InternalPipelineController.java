package com.smartmeeting.api.controller;

import com.smartmeeting.api.dto.ApiResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.config.InternalApiAuth;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.entity.PipelineStep;
import com.smartmeeting.entity.PipelineStepExecution;
import com.smartmeeting.entity.PipelineTemplate;
import com.smartmeeting.pipeline.PipelineStepDispatcher;
import com.smartmeeting.pipeline.StepExecutionResult;
import com.smartmeeting.pipeline.executor.PreAgendaOwnerConfirmNotifyStepExecutor;
import com.smartmeeting.repository.MeetingMapper;
import com.smartmeeting.repository.PipelineStepMapper;
import com.smartmeeting.repository.PipelineStepExecutionMapper;
import com.smartmeeting.repository.PipelineTemplateMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/internal/pipeline")
@RequiredArgsConstructor
public class InternalPipelineController {

    private final InternalApiAuth internalApiAuth;
    private final PipelineStepDispatcher pipelineStepDispatcher;
    private final MeetingMapper meetingMapper;
    private final PipelineStepExecutionMapper executionMapper;
    private final PipelineTemplateMapper templateMapper;
    private final PipelineStepMapper stepMapper;
    private final PreAgendaOwnerConfirmNotifyStepExecutor preAgendaOwnerConfirmNotifyStepExecutor;
    private final ObjectMapper objectMapper;

    @PostMapping("/execute")
    public ApiResponse<Map<String, Object>> execute(HttpServletRequest request, @RequestBody ExecuteReq body) {
        internalApiAuth.requireToken(request);
        int created = pipelineStepDispatcher.createExecutionPlan(body.getMeetingId(), body.getStage(), body.getTemplateCode());
        pipelineStepDispatcher.dispatchPending(body.getMeetingId(), body.getStage());
        return ApiResponse.ok(Map.of(
                "meetingId", body.getMeetingId(),
                "stage", body.getStage(),
                "created", created
        ));
    }

    @Data
    public static class ExecuteReq {
        private String meetingId;
        private String stage;
        private String templateCode;
    }

    @PostMapping("/execute-by-preset")
    public ApiResponse<Map<String, Object>> executeByPreset(HttpServletRequest request, @RequestBody ExecuteByPresetReq body) {
        internalApiAuth.requireToken(request);
        if (body == null || body.getPresetTypeCode() == null || body.getPresetTypeCode() <= 0) {
            throw new IllegalArgumentException("presetTypeCode 不能为空且必须为正整数");
        }
        String stage = (body.getStage() == null || body.getStage().isBlank()) ? "PRE" : body.getStage().trim().toUpperCase();
        String templateCode = (body.getTemplateCode() == null || body.getTemplateCode().isBlank())
                ? null
                : body.getTemplateCode().trim();

        PipelineTemplate template = templateMapper.selectOne(new LambdaQueryWrapper<PipelineTemplate>()
                .eq(PipelineTemplate::getStage, stage)
                .eq(PipelineTemplate::getEnabled, 1)
                .eq(templateCode != null, PipelineTemplate::getTemplateCode, templateCode)
                .last("LIMIT 1"));
        if (template == null) {
            throw new IllegalArgumentException("未找到可用 pipeline 模板: stage=" + stage);
        }
        List<PipelineStep> steps = stepMapper.selectList(new LambdaQueryWrapper<PipelineStep>()
                        .eq(PipelineStep::getTemplateId, template.getId())
                        .eq(PipelineStep::getEnabled, 1))
                .stream()
                .sorted(Comparator.comparingInt(s -> s.getOrderNo() == null ? Integer.MAX_VALUE : s.getOrderNo()))
                .toList();
        List<PipelineStep> targetSteps = steps.stream()
                .filter(s -> s != null && "pre-agenda-owner-confirm-notify".equalsIgnoreCase(s.getStepType()))
                .toList();
        if (targetSteps.isEmpty()) {
            throw new IllegalArgumentException("模板中不存在 pre-agenda-owner-confirm-notify 步骤");
        }
        String sharedContext = "{\"presetTypeCode\":" + body.getPresetTypeCode() + "}";
        int successCount = 0;
        int failedCount = 0;
        List<String> errors = new ArrayList<>();
        List<String> campaignIds = new ArrayList<>();
        for (PipelineStep step : targetSteps) {
            StepExecutionResult result = preAgendaOwnerConfirmNotifyStepExecutor.executeForPreset(
                    body.getPresetTypeCode(), step, stage, sharedContext);
            if (result.isSuccess()) {
                successCount++;
                JsonNode ctx = safeJson(result.getContextJson());
                String campaignId = ctx.path("agendaFillCampaignId").asText("");
                if (!campaignId.isBlank()) {
                    campaignIds.add(campaignId);
                }
                sharedContext = mergeContext(sharedContext, result.getContextJson());
            } else {
                failedCount++;
                errors.add((step.getStepCode() == null ? String.valueOf(step.getId()) : step.getStepCode())
                        + ": " + (result.getMessage() == null ? "failed" : result.getMessage()));
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("presetTypeCode", body.getPresetTypeCode());
        out.put("stage", stage);
        out.put("templateCode", template.getTemplateCode() == null ? "" : template.getTemplateCode());
        out.put("mode", "preset_direct");
        out.put("matchedMeetings", 0);
        out.put("triggeredMeetings", successCount);
        out.put("executedSteps", targetSteps.size());
        out.put("successSteps", successCount);
        out.put("failedSteps", failedCount);
        out.put("campaignIds", campaignIds);
        out.put("errors", errors);
        return ApiResponse.ok(out);
    }

    private JsonNode safeJson(String json) {
        try {
            return objectMapper.readTree(json == null || json.isBlank() ? "{}" : json);
        } catch (Exception ignored) {
            return objectMapper.createObjectNode();
        }
    }

    private String mergeContext(String baseJson, String deltaJson) {
        try {
            JsonNode base = objectMapper.readTree(baseJson == null || baseJson.isBlank() ? "{}" : baseJson);
            JsonNode delta = objectMapper.readTree(deltaJson == null || deltaJson.isBlank() ? "{}" : deltaJson);
            com.fasterxml.jackson.databind.node.ObjectNode out = objectMapper.createObjectNode();
            if (base.isObject()) {
                out.setAll((com.fasterxml.jackson.databind.node.ObjectNode) base);
            }
            if (delta.isObject()) {
                out.setAll((com.fasterxml.jackson.databind.node.ObjectNode) delta);
            }
            return out.toString();
        } catch (Exception ignored) {
            return baseJson;
        }
    }

    @Data
    public static class ExecuteByPresetReq {
        private Integer presetTypeCode;
        private String stage;
        private String templateCode;
        private Boolean skipExisting;
    }
}

package com.smartmeeting.admin.api.controller;

import com.smartmeeting.admin.api.dto.ApiResponse;
import com.smartmeeting.admin.api.dto.PipelineExecutionDto;
import com.smartmeeting.admin.api.dto.PipelineStepDto;
import com.smartmeeting.admin.api.dto.PipelineTemplateDto;
import com.smartmeeting.admin.service.PipelineAdminService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/pipeline")
@RequiredArgsConstructor
public class PipelineAdminController {

    private final PipelineAdminService pipelineAdminService;

    @GetMapping("/templates")
    public ApiResponse<List<PipelineTemplateDto>> listTemplates() {
        return ApiResponse.ok(pipelineAdminService.listTemplates());
    }

    @PostMapping("/templates")
    public ApiResponse<Map<String, Long>> saveTemplate(@RequestBody PipelineTemplateDto body) {
        return ApiResponse.ok(Map.of("id", pipelineAdminService.saveTemplate(body)));
    }

    @GetMapping("/steps")
    public ApiResponse<List<PipelineStepDto>> listSteps(@RequestParam long templateId) {
        return ApiResponse.ok(pipelineAdminService.listSteps(templateId));
    }

    @PostMapping("/steps")
    public ApiResponse<Map<String, Long>> saveStep(@RequestBody PipelineStepDto body) {
        return ApiResponse.ok(Map.of("id", pipelineAdminService.saveStep(body)));
    }

    @DeleteMapping("/templates/{id}")
    public ApiResponse<Void> deleteTemplate(@PathVariable long id) {
        pipelineAdminService.deleteTemplate(id);
        return ApiResponse.ok();
    }

    @DeleteMapping("/steps/{id}")
    public ApiResponse<Void> deleteStep(@PathVariable long id) {
        pipelineAdminService.deleteStep(id);
        return ApiResponse.ok();
    }

    @GetMapping("/executions")
    public ApiResponse<List<PipelineExecutionDto>> listExecutions(@RequestParam(required = false) String meetingId) {
        return ApiResponse.ok(pipelineAdminService.listExecutions(meetingId));
    }

    @PostMapping("/execute")
    public ApiResponse<Map<String, Object>> execute(@RequestBody ExecuteReq body) {
        return ApiResponse.ok(pipelineAdminService.executePipeline(
                body != null ? body.getMeetingId() : null,
                body != null ? body.getPresetTypeCode() : null,
                body != null ? body.getStage() : null,
                body != null ? body.getTemplateCode() : null,
                body != null ? body.getSkipExisting() : null));
    }

    @Data
    public static class ExecuteReq {
        private String meetingId;
        private Integer presetTypeCode;
        private String stage;
        private String templateCode;
        private Boolean skipExisting;
    }
}

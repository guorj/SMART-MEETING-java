package com.smartmeeting.api.controller;

import com.smartmeeting.api.dto.ApiResponse;
import com.smartmeeting.config.InternalApiAuth;
import com.smartmeeting.pipeline.PipelineStepDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/internal/pipeline")
@RequiredArgsConstructor
public class InternalPipelineController {

    private final InternalApiAuth internalApiAuth;
    private final PipelineStepDispatcher pipelineStepDispatcher;

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
}

package com.smartmeeting.service;

import com.smartmeeting.config.MeetingPipelineProperties;
import com.smartmeeting.pipeline.PipelineStepDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingPreStageService {

    private final PipelineStepDispatcher pipelineStepDispatcher;
    private final MeetingPipelineProperties pipelineProperties;

    public void runPreStage(String meetingId, Integer presetTypeCode) {
        if (!pipelineProperties.isPreOnCreateEnabled()) {
            log.info("Pre stage pipeline disabled by config: meetingId={}, presetTypeCode={}", meetingId, presetTypeCode);
            return;
        }
        try {
            // 会前 6 步在当前版本由可配置 Pipeline 编排执行，模板可按 preset 进一步拆分。
            pipelineStepDispatcher.createExecutionPlan(meetingId, "PRE", null);
            pipelineStepDispatcher.dispatchPending(meetingId, "PRE");
            log.info("Pre stage pipeline finished: meetingId={}, presetTypeCode={}", meetingId, presetTypeCode);
        } catch (Exception e) {
            log.warn("Pre stage pipeline skipped: meetingId={}, reason={}", meetingId, e.getMessage());
        }
    }
}

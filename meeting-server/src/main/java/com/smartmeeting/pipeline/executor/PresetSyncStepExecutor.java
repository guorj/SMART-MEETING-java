package com.smartmeeting.pipeline.executor;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartmeeting.pipeline.StepExecutionContext;
import com.smartmeeting.pipeline.StepExecutionResult;
import com.smartmeeting.pipeline.StepExecutor;
import com.smartmeeting.service.PresetAgendaDocService;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PresetSyncStepExecutor implements StepExecutor {
    private final PresetAgendaDocService presetAgendaDocService;
    private final PipelineExecutorSupport support;

    @Override
    public String stepType() {
        return "preset-sync";
    }

    @Override
    public StepExecutionResult execute(StepExecutionContext context) {
        JsonNode cfg = support.parseConfig(context.getStep().getConfigJson());
        int presetTypeCode = support.number(cfg, "presetTypeCode", 0);
        if (presetTypeCode > 0) {
            presetAgendaDocService.refreshPresetBundle(presetTypeCode);
            log.info("Pipeline preset-sync executed: meetingId={}, step={}, presetTypeCode={}",
                    context.getMeetingId(), context.getStep().getStepCode(), presetTypeCode);
            return StepExecutionResult.ok("preset-sync-one", "{\"presetTypeCode\":" + presetTypeCode + "}");
        }
        presetAgendaDocService.refreshAllPresetBundles();
        log.info("Pipeline preset-sync executed: meetingId={}, step={}, all presets",
                context.getMeetingId(), context.getStep().getStepCode());
        return StepExecutionResult.ok("preset-sync-all", "{\"all\":true}");
    }
}

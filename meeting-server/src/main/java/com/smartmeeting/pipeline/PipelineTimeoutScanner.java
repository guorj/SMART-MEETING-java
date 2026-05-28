package com.smartmeeting.pipeline;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PipelineTimeoutScanner {

    private final PipelineStepDispatcher pipelineStepDispatcher;

    @Scheduled(fixedDelayString = "${meeting.pipeline.timeout-scan-ms:10000}")
    public void scan() {
        int affected = pipelineStepDispatcher.markTimeoutExecutions();
        if (affected > 0) {
            log.warn("Pipeline timeout scanner marked {} rows as TIMEOUT", affected);
        }
    }
}

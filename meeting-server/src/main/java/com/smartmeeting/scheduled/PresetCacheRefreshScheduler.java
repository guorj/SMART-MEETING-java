package com.smartmeeting.scheduled;

import com.smartmeeting.service.PresetAgendaDocService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时将 preset 1～5 的会务预设与飞书资料配置从 DB 预热到 Redis。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "meeting.cache.refresh-enabled", havingValue = "true", matchIfMissing = true)
public class PresetCacheRefreshScheduler {

    private final PresetAgendaDocService presetAgendaDocService;

    @Scheduled(cron = "${meeting.cache.refresh-cron:0 */10 * * * ?}")
    public void refreshAllPresetCaches() {
        log.debug("Preset cache scheduled refresh started");
        presetAgendaDocService.refreshAllPresetBundles();
        log.debug("Preset cache scheduled refresh finished");
    }
}

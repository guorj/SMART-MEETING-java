package com.smartmeeting.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.config.agenda.AgendaDocBindingSnapshot;
import com.smartmeeting.config.agenda.PresetAgendaMergeEngine;
import com.smartmeeting.config.feishu.FeishuResourceRef;
import com.smartmeeting.config.feishu.FeishuResourceResolver;
import com.smartmeeting.repository.MeetingMinuteMapper;
import com.smartmeeting.service.PresetAgendaDocService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 数据库种子数据启动校验器。
 * <p>
 * 应用就绪后只读校验 preset=1 的 host_agenda v2 内嵌飞书资料；不执行任何 INSERT/UPDATE。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseSeedStartupValidator {

    private static final int PRESET_COMPREHENSIVE = 1;
    private static final Set<Integer> EXPECTED_AGENDA_INDICES = Set.of(1, 2, 3, 4);

    private final Environment environment;
    private final MeetingDatabaseProperties databaseProperties;
    private final MeetingMinuteMapper meetingMinuteMapper;
    private final PresetAgendaDocService presetAgendaDocService;
    private final ObjectMapper objectMapper;

    @EventListener(ApplicationReadyEvent.class)
    public void validateOnStartup() {
        guardSqlInitMode();
        if (!databaseProperties.isValidateSeedOnStartup()) {
            log.info("会序飞书配置启动校验已关闭 (meeting.database.validate-seed-on-startup=false)");
            return;
        }
        if (isTestProfile()) {
            return;
        }
        validatePresetAgendaDocConfigs();
        validateWeeklyReportBindings();
        refreshPresetCachesAfterValidation();
        validateMeetingMinuteTable();
    }

    private void refreshPresetCachesAfterValidation() {
        try {
            presetAgendaDocService.refreshPresetBundle(PRESET_COMPREHENSIVE);
            log.info("已刷新 preset={} Redis/本地缓存", PRESET_COMPREHENSIVE);
        } catch (Exception e) {
            log.warn("刷新 preset 缓存失败: {}", e.getMessage());
        }
    }

    private void validateWeeklyReportBindings() {
        for (int idx : EXPECTED_AGENDA_INDICES) {
            var binding = presetAgendaDocService.findReportBindingForAgenda(PRESET_COMPREHENSIVE, idx);
            if (binding.isPresent() && binding.get().generatedReportUrl() != null
                    && !binding.get().generatedReportUrl().isBlank()) {
                log.info("会前对比通报已绑定: preset=1 agenda_index={} url={}",
                        idx, binding.get().generatedReportUrl());
            } else {
                log.warn("会前对比通报未就绪 preset=1 agenda_index={}（需 feishu-scheduled-bot 定时任务写回 generated_report_url）",
                        idx);
            }
        }
    }

    private void validateMeetingMinuteTable() {
        try {
            meetingMinuteMapper.selectCount(null);
            log.debug("int_meeting_minute 表校验通过");
        } catch (Exception e) {
            log.warn("int_meeting_minute 表不可用，请执行 schema-upgrade/v0.5-minute.sql: {}",
                    e.getMessage());
        }
    }

    private void guardSqlInitMode() {
        if (isTestProfile()) {
            return;
        }
        String mode = environment.getProperty("spring.sql.init.mode", "never");
        if ("never".equalsIgnoreCase(mode)) {
            return;
        }
        String initScripts = String.join(",",
                environment.getProperty("spring.sql.init.data-locations", ""),
                environment.getProperty("spring.sql.init.schema-locations", ""));
        boolean loadsSeedData = initScripts.contains("schema-data");
        if (loadsSeedData || "always".equalsIgnoreCase(mode)) {
            log.error("spring.sql.init.mode={} 且可能加载 schema-data，应用重启会覆盖库内飞书 URL。"
                    + "请将 spring.sql.init.mode 设为 never（application.yml 已默认 never）。", mode);
        } else {
            log.warn("spring.sql.init.mode={}，请确认不会在每次启动时执行 DML 种子脚本", mode);
        }
    }

    private void validatePresetAgendaDocConfigs() {
        var preset = presetAgendaDocService.getPresetCached(PRESET_COMPREHENSIVE);
        String json = preset != null ? preset.getHostAgenda() : null;
        List<AgendaDocBindingSnapshot> rows = PresetAgendaMergeEngine.extractBindingsFromHostAgenda(
                PRESET_COMPREHENSIVE, json, objectMapper).stream()
                .filter(b -> b.getEnabled() != null && b.getEnabled() == 1)
                .filter(b -> b.getAgendaIndex() != null && EXPECTED_AGENDA_INDICES.contains(b.getAgendaIndex()))
                .toList();

        List<String> issues = new ArrayList<>();
        Set<Integer> found = rows.stream()
                .map(AgendaDocBindingSnapshot::getAgendaIndex)
                .collect(Collectors.toSet());
        for (int idx : EXPECTED_AGENDA_INDICES) {
            if (!found.contains(idx)) {
                issues.add("preset=1 host_agenda 缺少 agenda_index=" + idx + " 的已启用飞书资料");
            }
        }
        for (AgendaDocBindingSnapshot cfg : rows) {
            int idx = cfg.getAgendaIndex();
            String url = cfg.getFeishuDocUrl();
            if (url == null || url.isBlank()) {
                issues.add("agenda_index=" + idx + " url 为空");
                continue;
            }
            FeishuResourceRef ref = FeishuResourceResolver.resolve(url);
            if (ref == null || !ref.showOnHostPage()) {
                issues.add("agenda_index=" + idx + " URL 无法解析: " + abbreviate(url, 72));
            }
        }
        if (issues.isEmpty()) {
            log.info("会序飞书配置启动校验通过: preset=1 host_agenda 内嵌 {} 条 agenda_index 1–4", rows.size());
            return;
        }
        log.warn("会序飞书配置启动校验发现问题（{} 条）: {}", issues.size(), String.join("; ", issues));
    }

    private boolean isTestProfile() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(p -> "test".equalsIgnoreCase(p) || p.toLowerCase().contains("test"));
    }

    private static String abbreviate(String s, int max) {
        if (s == null || s.length() <= max) {
            return s;
        }
        return s.substring(0, max - 3) + "...";
    }
}

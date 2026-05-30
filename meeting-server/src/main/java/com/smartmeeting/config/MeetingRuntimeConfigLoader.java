package com.smartmeeting.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.entity.MeetingSystemConfig;
import com.smartmeeting.repository.MeetingSystemConfigMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MeetingRuntimeConfigLoader {

    private final MeetingSystemConfigMapper configMapper;
    private final MeetingRuntimeConfig runtimeConfig;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        reload();
    }

    public synchronized void reload() {
        List<MeetingSystemConfig> rows;
        try {
            rows = configMapper.selectList(null);
        } catch (Exception e) {
            log.warn("runtime config reload skipped: {}", e.getMessage());
            return;
        }
        if (rows == null || rows.isEmpty()) {
            log.debug("runtime config reload: no DB overrides");
            return;
        }
        for (MeetingSystemConfig row : rows) {
            apply(row.getConfigKey(), row.getValueJson());
        }
        log.info("runtime config reload applied {} row(s)", rows.size());
    }

    private void apply(String key, String valueJson) {
        if (key == null || valueJson == null) {
            return;
        }
        try {
            JsonNode node = objectMapper.readTree(valueJson);
            boolean bool = node.isBoolean() ? node.booleanValue() : Boolean.parseBoolean(node.asText());
            int num = node.isNumber() ? node.intValue() : Integer.parseInt(node.asText());
            switch (key) {
                case "meeting.host.enabled" -> runtimeConfig.setEnabled(bool);
                case "meeting.host.agenda-enabled" -> runtimeConfig.setAgendaEnabled(bool);
                case "meeting.host.tts-enabled" -> runtimeConfig.setTtsEnabled(bool);
                case "meeting.host.roll-call-enabled" -> runtimeConfig.setRollCallEnabled(bool);
                case "meeting.host.auto-roll-call-after-opening" -> runtimeConfig.setAutoRollCallAfterOpening(bool);
                case "meeting.host.topic-timeout-strategy" -> runtimeConfig.setTopicTimeoutStrategy(node.asText("REMIND_ONLY"));
                case "meeting.host.roll-call.window-seconds" -> runtimeConfig.getRollCall().setWindowSeconds(num);
                case "meeting.host.roll-call.asr-grace-seconds" -> runtimeConfig.getRollCall().setAsrGraceSeconds(num);
                case "meeting.host.roll-call.online-inventory-seconds" -> runtimeConfig.getRollCall().setOnlineInventorySeconds(num);
                default -> log.trace("runtime config key not mapped: {}", key);
            }
        } catch (Exception e) {
            log.warn("skip invalid runtime config {}={}: {}", key, valueJson, e.getMessage());
        }
    }
}

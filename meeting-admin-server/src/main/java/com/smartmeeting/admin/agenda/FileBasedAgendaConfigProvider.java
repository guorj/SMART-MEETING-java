package com.smartmeeting.admin.agenda;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.admin.config.AgendaFileProperties;
import com.smartmeeting.config.agenda.AgendaPresetSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 可选只读会序源：从本地 JSON 文件加载（便于 Git 管理模板）。
 * 启用：meeting.admin.agenda-config.file-enabled=true
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "meeting.admin.agenda-config", name = "file-enabled", havingValue = "true")
public class FileBasedAgendaConfigProvider implements com.smartmeeting.config.agenda.AgendaConfigProvider {

    private final AgendaFileProperties properties;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

    public FileBasedAgendaConfigProvider(AgendaFileProperties properties,
                                         ResourceLoader resourceLoader,
                                         ObjectMapper objectMapper) {
        this.properties = properties;
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
    }

    @Override
    public String providerId() {
        return "file_based";
    }

    @Override
    public int order() {
        return 5;
    }

    @Override
    public Optional<AgendaPresetSnapshot> loadPreset(int presetTypeCode) {
        String path = properties.getFilePath();
        if (path == null || path.isBlank()) {
            return Optional.empty();
        }
        try {
            Resource res = resourceLoader.getResource(path);
            if (!res.exists()) {
                log.warn("agenda file not found: {}", path);
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(res.getInputStream());
            JsonNode preset = root.path(String.valueOf(presetTypeCode));
            if (preset.isMissingNode()) {
                preset = root.path("preset" + presetTypeCode);
            }
            if (preset.isMissingNode()) {
                return Optional.empty();
            }
            String hostJson;
            if (preset.has("hostAgenda")) {
                hostJson = objectMapper.writeValueAsString(preset.get("hostAgenda"));
            } else if (preset.has("host_agenda")) {
                JsonNode ha = preset.get("host_agenda");
                hostJson = ha.isTextual() ? ha.asText() : objectMapper.writeValueAsString(ha);
            } else {
                hostJson = null;
            }
            return Optional.of(AgendaPresetSnapshot.builder()
                    .presetTypeCode(presetTypeCode)
                    .displayName(preset.path("displayName").asText(null))
                    .hostAgendaJson(hostJson)
                    .build());
        } catch (Exception e) {
            log.warn("file_based agenda load failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void savePreset(AgendaPresetSnapshot snapshot) {
        throw new UnsupportedOperationException("file_based provider is read-only");
    }
}

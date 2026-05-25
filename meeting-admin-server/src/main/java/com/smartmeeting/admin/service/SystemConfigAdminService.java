package com.smartmeeting.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmeeting.admin.api.dto.SystemConfigAuditDto;
import com.smartmeeting.admin.api.dto.SystemConfigEntryDto;
import com.smartmeeting.admin.api.dto.SystemConfigSchemaItemDto;
import com.smartmeeting.admin.entity.MeetingSystemConfigAudit;
import com.smartmeeting.admin.repository.MeetingSystemConfigAuditMapper;
import com.smartmeeting.admin.config.ConfigDescriptorRegistry;
import com.smartmeeting.admin.service.MeetingServerBridgeService;
import com.smartmeeting.admin.entity.MeetingSystemConfig;
import com.smartmeeting.admin.exception.BusinessException;
import com.smartmeeting.admin.repository.MeetingSystemConfigMapper;
import com.smartmeeting.config.system.SystemConfigDescriptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemConfigAdminService {

    private final ConfigDescriptorRegistry descriptorRegistry;
    private final MeetingSystemConfigMapper configMapper;
    private final MeetingSystemConfigAuditMapper auditMapper;
    private final MeetingServerBridgeService meetingServerBridge;

    public List<SystemConfigSchemaItemDto> schema() {
        Map<String, String> dbValues = configMapper.selectList(null).stream()
                .collect(Collectors.toMap(MeetingSystemConfig::getConfigKey,
                        MeetingSystemConfig::getValueJson, (a, b) -> b));
        List<SystemConfigSchemaItemDto> items = new ArrayList<>();
        for (SystemConfigDescriptor d : descriptorRegistry.all()) {
            items.add(SystemConfigSchemaItemDto.builder()
                    .key(d.key())
                    .category(d.category())
                    .type(d.type().name())
                    .defaultValue(d.defaultValue())
                    .currentValue(dbValues.getOrDefault(d.key(), null))
                    .hotReloadable(d.hotReloadable())
                    .description(d.description())
                    .sensitive(d.sensitive())
                    .build());
        }
        return items;
    }

    public void upsert(String key, String valueJson) {
        SystemConfigDescriptor d = descriptorRegistry.find(key)
                .orElseThrow(() -> new BusinessException("unknown config key"));
        if (d.sensitive()) {
            throw new BusinessException("sensitive key cannot be edited");
        }
        d.validator().ifPresent(v -> {
            if (!v.test(valueJson)) {
                throw new BusinessException("validation failed for " + key);
            }
        });
        LambdaQueryWrapper<MeetingSystemConfig> q = new LambdaQueryWrapper<>();
        q.eq(MeetingSystemConfig::getConfigKey, key);
        MeetingSystemConfig existing = configMapper.selectOne(q);
        String oldJson = existing != null ? existing.getValueJson() : null;
        if (existing == null) {
            MeetingSystemConfig row = new MeetingSystemConfig();
            row.setConfigKey(key);
            row.setCategory(d.category());
            row.setValueJson(valueJson);
            row.setDescription(d.description());
            configMapper.insert(row);
        } else {
            existing.setValueJson(valueJson);
            configMapper.updateById(existing);
        }
        recordAudit(key, "UPSERT", oldJson, valueJson);
    }

    public void delete(String key) {
        LambdaQueryWrapper<MeetingSystemConfig> q = new LambdaQueryWrapper<>();
        q.eq(MeetingSystemConfig::getConfigKey, key);
        MeetingSystemConfig existing = configMapper.selectOne(q);
        String oldJson = existing != null ? existing.getValueJson() : null;
        configMapper.delete(q);
        recordAudit(key, "DELETE", oldJson, null);
    }

    private void recordAudit(String key, String action, String oldJson, String newJson) {
        try {
            MeetingSystemConfigAudit row = new MeetingSystemConfigAudit();
            row.setConfigKey(key);
            row.setAction(action);
            row.setOldValueJson(oldJson);
            row.setNewValueJson(newJson);
            row.setOperator("admin");
            auditMapper.insert(row);
        } catch (Exception e) {
            log.warn("config audit insert failed: {}", e.getMessage());
        }
    }

    public List<SystemConfigAuditDto> listAudit(String configKey, int limit) {
        int cap = Math.min(Math.max(limit, 1), 100);
        LambdaQueryWrapper<MeetingSystemConfigAudit> q = new LambdaQueryWrapper<>();
        if (configKey != null && !configKey.isBlank()) {
            q.eq(MeetingSystemConfigAudit::getConfigKey, configKey);
        }
        q.orderByDesc(MeetingSystemConfigAudit::getCreatedAt).last("LIMIT " + cap);
        return auditMapper.selectList(q).stream()
                .map(a -> SystemConfigAuditDto.builder()
                        .id(a.getId())
                        .configKey(a.getConfigKey())
                        .action(a.getAction())
                        .oldValueJson(a.getOldValueJson())
                        .newValueJson(a.getNewValueJson())
                        .operator(a.getOperator())
                        .createdAt(a.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    public boolean triggerRuntimeReload() {
        return meetingServerBridge.triggerRuntimeReload();
    }

    public List<SystemConfigEntryDto> listEntries() {
        return configMapper.selectList(null).stream()
                .map(r -> SystemConfigEntryDto.builder()
                        .key(r.getConfigKey())
                        .category(r.getCategory())
                        .valueJson(r.getValueJson())
                        .build())
                .collect(Collectors.toList());
    }
}

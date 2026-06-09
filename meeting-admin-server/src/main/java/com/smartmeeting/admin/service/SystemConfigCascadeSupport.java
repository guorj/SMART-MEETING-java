package com.smartmeeting.admin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.admin.exception.BusinessException;
import com.smartmeeting.admin.config.ConfigDescriptorRegistry;
import com.smartmeeting.config.system.ConfigValueType;
import com.smartmeeting.config.system.SystemConfigDescriptor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class SystemConfigCascadeSupport {

    private final ConfigDescriptorRegistry descriptorRegistry;
    private final ObjectMapper objectMapper;

    public boolean isParentEffectiveOn(String parentKey, Map<String, String> dbValues) {
        if (parentKey == null || parentKey.isBlank()) {
            return true;
        }
        return readBoolean(parentKey, dbValues);
    }

    public boolean isEditable(SystemConfigDescriptor d, Map<String, String> dbValues) {
        return d.parentKey()
                .map(pk -> isParentEffectiveOn(pk, dbValues))
                .orElse(true);
    }

    public void validateUpsert(SystemConfigDescriptor d, String valueJson, Map<String, String> dbValues) {
        d.parentKey().ifPresent(pk -> {
            if (!isParentEffectiveOn(pk, dbValues)) {
                if (d.type() == ConfigValueType.BOOLEAN && parseBoolean(valueJson)) {
                    throw new BusinessException("父项 " + pk + " 已关闭，无法单独开启 " + d.key());
                }
            }
        });
    }

    private boolean readBoolean(String key, Map<String, String> dbValues) {
        String json = dbValues.get(key);
        if (json != null) {
            return parseBoolean(json);
        }
        return descriptorRegistry.find(key)
                .map(d -> Boolean.parseBoolean(d.defaultValue()))
                .orElse(true);
    }

    private boolean parseBoolean(String valueJson) {
        try {
            JsonNode node = objectMapper.readTree(valueJson);
            if (node.isBoolean()) {
                return node.booleanValue();
            }
            return Boolean.parseBoolean(node.asText());
        } catch (Exception e) {
            return false;
        }
    }
}

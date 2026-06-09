package com.smartmeeting.admin.system;

import com.smartmeeting.config.system.ConfigValueType;
import com.smartmeeting.config.system.SystemConfigDescriptor;

import java.util.Optional;

abstract class AbstractBooleanDescriptor implements SystemConfigDescriptor {

    private final String key;
    private final String category;
    private final String defaultValue;
    private final String description;
    private final String parentKey;

    AbstractBooleanDescriptor(String key, String category, String defaultValue, String description) {
        this(key, category, defaultValue, description, null);
    }

    AbstractBooleanDescriptor(String key, String category, String defaultValue, String description, String parentKey) {
        this.key = key;
        this.category = category;
        this.defaultValue = defaultValue;
        this.description = description;
        this.parentKey = parentKey;
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    public String category() {
        return category;
    }

    @Override
    public ConfigValueType type() {
        return ConfigValueType.BOOLEAN;
    }

    @Override
    public String defaultValue() {
        return defaultValue;
    }

    @Override
    public boolean hotReloadable() {
        return true;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public Optional<String> parentKey() {
        return Optional.ofNullable(parentKey);
    }
}

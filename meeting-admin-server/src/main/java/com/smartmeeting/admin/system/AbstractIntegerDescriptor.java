package com.smartmeeting.admin.system;

import com.smartmeeting.config.system.ConfigJsonValidators;
import com.smartmeeting.config.system.ConfigValueType;
import com.smartmeeting.config.system.SystemConfigDescriptor;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Predicate;

abstract class AbstractIntegerDescriptor implements SystemConfigDescriptor {

    private final String key;
    private final String category;
    private final String defaultValue;
    private final String description;
    private final String parentKey;
    private final OptionalInt intMin;
    private final OptionalInt intMax;
    private final String rangeHintOverride;

    AbstractIntegerDescriptor(String key, String category, String defaultValue, String description) {
        this(key, category, defaultValue, description, null, OptionalInt.empty(), OptionalInt.empty(), null);
    }

    AbstractIntegerDescriptor(String key, String category, String defaultValue, String description, String parentKey) {
        this(key, category, defaultValue, description, parentKey, OptionalInt.empty(), OptionalInt.empty(), null);
    }

    AbstractIntegerDescriptor(String key, String category, String defaultValue, String description,
                              String parentKey, int intMin, int intMax) {
        this(key, category, defaultValue, description, parentKey, OptionalInt.of(intMin), OptionalInt.of(intMax), null);
    }

    AbstractIntegerDescriptor(String key, String category, String defaultValue, String description,
                              String parentKey, int intMin, int intMax, String rangeHintOverride) {
        this(key, category, defaultValue, description, parentKey,
                OptionalInt.of(intMin), OptionalInt.of(intMax), rangeHintOverride);
    }

    private AbstractIntegerDescriptor(String key, String category, String defaultValue, String description,
                                      String parentKey, OptionalInt intMin, OptionalInt intMax,
                                      String rangeHintOverride) {
        this.key = key;
        this.category = category;
        this.defaultValue = defaultValue;
        this.description = description;
        this.parentKey = parentKey;
        this.intMin = intMin;
        this.intMax = intMax;
        this.rangeHintOverride = rangeHintOverride;
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
        return ConfigValueType.INTEGER;
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

    @Override
    public OptionalInt intMin() {
        return intMin;
    }

    @Override
    public OptionalInt intMax() {
        return intMax;
    }

    @Override
    public String valueRangeHint() {
        if (rangeHintOverride != null && !rangeHintOverride.isBlank()) {
            return rangeHintOverride;
        }
        return SystemConfigDescriptor.super.valueRangeHint();
    }

    @Override
    public Optional<Predicate<String>> validator() {
        if (intMin.isPresent() && intMax.isPresent()) {
            return Optional.of(ConfigJsonValidators.intInRange(intMin.getAsInt(), intMax.getAsInt()));
        }
        return Optional.empty();
    }
}

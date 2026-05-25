package com.smartmeeting.admin.config;

import com.smartmeeting.config.system.SystemConfigDescriptor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ConfigDescriptorRegistry {

    private final Map<String, SystemConfigDescriptor> byKey;

    public ConfigDescriptorRegistry(List<SystemConfigDescriptor> descriptors) {
        this.byKey = descriptors.stream()
                .collect(Collectors.toMap(SystemConfigDescriptor::key, Function.identity(),
                        (a, b) -> a));
    }

    public List<SystemConfigDescriptor> all() {
        return byKey.values().stream()
                .sorted(Comparator.comparing(SystemConfigDescriptor::category)
                        .thenComparing(SystemConfigDescriptor::key))
                .collect(Collectors.toList());
    }

    public Optional<SystemConfigDescriptor> find(String key) {
        return Optional.ofNullable(byKey.get(key));
    }
}

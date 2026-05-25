package com.smartmeeting.config.agenda;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 聚合已注册的会序配置 Provider（无 Spring 依赖，由应用层注入列表构造）。
 */
public class AgendaConfigProviderRegistry {

    private final List<AgendaConfigProvider> providers;

    public AgendaConfigProviderRegistry(List<AgendaConfigProvider> providers) {
        this.providers = providers == null ? List.of() : providers.stream()
                .sorted(Comparator.comparingInt(AgendaConfigProvider::order))
                .collect(Collectors.toList());
    }

    public List<AgendaConfigProvider> all() {
        return List.copyOf(providers);
    }

    public List<String> providerIds() {
        return providers.stream().map(AgendaConfigProvider::providerId).collect(Collectors.toList());
    }
}

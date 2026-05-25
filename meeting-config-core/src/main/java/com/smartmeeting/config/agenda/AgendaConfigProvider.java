package com.smartmeeting.config.agenda;

import java.util.Optional;

/**
 * 会序配置数据源（L2）。实现类由 Spring 注册到 {@link AgendaConfigProviderRegistry}。
 */
public interface AgendaConfigProvider {

    String providerId();

    int order();

    Optional<AgendaPresetSnapshot> loadPreset(int presetTypeCode);

    void savePreset(AgendaPresetSnapshot snapshot);
}

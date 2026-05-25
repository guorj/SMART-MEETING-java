package com.smartmeeting.admin.config;

import com.smartmeeting.config.agenda.AgendaConfigProvider;
import com.smartmeeting.config.agenda.AgendaConfigProviderRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class AgendaConfigBeans {

    @Bean
    public AgendaConfigProviderRegistry agendaConfigProviderRegistry(List<AgendaConfigProvider> providers) {
        return new AgendaConfigProviderRegistry(providers);
    }
}

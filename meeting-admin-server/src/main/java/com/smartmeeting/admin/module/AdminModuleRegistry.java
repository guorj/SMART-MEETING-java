package com.smartmeeting.admin.module;

import com.smartmeeting.admin.config.AdminProperties;
import com.smartmeeting.config.admin.AdminModule;
import com.smartmeeting.config.admin.AdminModuleManifest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AdminModuleRegistry {

    private final List<AdminModule> modules;
    private final AdminProperties adminProperties;

    public List<AdminModuleManifest> manifests() {
        List<String> allow = adminProperties.getModules();
        boolean filter = allow != null && !allow.isEmpty();
        return modules.stream()
                .sorted(Comparator.comparingInt(AdminModule::order))
                .filter(m -> !filter || allow.contains(m.moduleId()))
                .map(this::toManifest)
                .collect(Collectors.toList());
    }

    private AdminModuleManifest toManifest(AdminModule m) {
        AdminModuleManifest base = m.manifest();
        boolean enabled = m.enabledByDefault();
        List<String> allow = adminProperties.getModules();
        if (allow != null && !allow.isEmpty() && !allow.contains(m.moduleId())) {
            enabled = false;
        }
        return new AdminModuleManifest(
                base.getModuleId(),
                base.getDisplayName(),
                base.getOrder(),
                base.getApiPathPrefix(),
                base.getUiRouteHash(),
                enabled,
                base.getScriptPath());
    }
}

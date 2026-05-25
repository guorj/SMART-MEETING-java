package com.smartmeeting.admin.module;

import com.smartmeeting.config.admin.AdminModule;
import com.smartmeeting.config.admin.AdminModuleManifest;
import org.springframework.stereotype.Component;

@Component
public class ObservabilityAdminModule implements AdminModule {

    @Override
    public String moduleId() {
        return "observability";
    }

    @Override
    public String displayName() {
        return "数据查看";
    }

    @Override
    public int order() {
        return 50;
    }

    @Override
    public String apiPathPrefix() {
        return "/api/v1/admin/observability";
    }

    @Override
    public String uiRouteHash() {
        return "#/observability";
    }

    @Override
    public AdminModuleManifest manifest() {
        return new AdminModuleManifest(moduleId(), displayName(), order(), apiPathPrefix(), uiRouteHash(),
                true, "/static/admin/modules/observability.js");
    }
}

package com.smartmeeting.admin.module;

import com.smartmeeting.config.admin.AdminModule;
import com.smartmeeting.config.admin.AdminModuleManifest;
import org.springframework.stereotype.Component;

@Component
public class IntegrationsAdminModule implements AdminModule {

    @Override
    public String moduleId() {
        return "integrations";
    }

    @Override
    public String displayName() {
        return "集成入口";
    }

    @Override
    public int order() {
        return 60;
    }

    @Override
    public String apiPathPrefix() {
        return "/api/v1/admin/integrations";
    }

    @Override
    public String uiRouteHash() {
        return "#/integrations";
    }

    @Override
    public AdminModuleManifest manifest() {
        return new AdminModuleManifest(moduleId(), displayName(), order(), apiPathPrefix(), uiRouteHash(),
                true, "/static/admin/modules/integrations.js");
    }
}

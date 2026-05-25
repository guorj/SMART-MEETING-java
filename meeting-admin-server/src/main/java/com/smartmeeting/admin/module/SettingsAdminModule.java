package com.smartmeeting.admin.module;

import com.smartmeeting.config.admin.AdminModule;
import com.smartmeeting.config.admin.AdminModuleManifest;
import org.springframework.stereotype.Component;

@Component
public class SettingsAdminModule implements AdminModule {

    @Override
    public String moduleId() {
        return "settings";
    }

    @Override
    public String displayName() {
        return "系统参数";
    }

    @Override
    public int order() {
        return 40;
    }

    @Override
    public String apiPathPrefix() {
        return "/api/v1/admin/system-config";
    }

    @Override
    public String uiRouteHash() {
        return "#/settings";
    }

    @Override
    public AdminModuleManifest manifest() {
        return new AdminModuleManifest(moduleId(), displayName(), order(), apiPathPrefix(), uiRouteHash(),
                true, "/static/admin/modules/settings.js");
    }
}

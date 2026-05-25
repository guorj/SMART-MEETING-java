package com.smartmeeting.admin.module;

import com.smartmeeting.config.admin.AdminModule;
import com.smartmeeting.config.admin.AdminModuleManifest;
import org.springframework.stereotype.Component;

@Component
public class PresetsAdminModule implements AdminModule {

    @Override
    public String moduleId() {
        return "presets";
    }

    @Override
    public String displayName() {
        return "会序与资料";
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public String apiPathPrefix() {
        return "/api/v1/admin/agenda-config";
    }

    @Override
    public String uiRouteHash() {
        return "#/presets";
    }

    @Override
    public AdminModuleManifest manifest() {
        return new AdminModuleManifest(moduleId(), displayName(), order(), apiPathPrefix(), uiRouteHash(),
                true, "/static/admin/modules/presets.js");
    }
}

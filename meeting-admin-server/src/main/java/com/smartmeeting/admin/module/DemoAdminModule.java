package com.smartmeeting.admin.module;

import com.smartmeeting.config.admin.AdminModule;
import com.smartmeeting.config.admin.AdminModuleManifest;
import org.springframework.stereotype.Component;

/** 扩展模板模块，默认不启用。 */
@Component
public class DemoAdminModule implements AdminModule {

    @Override
    public String moduleId() {
        return "demo";
    }

    @Override
    public String displayName() {
        return "Demo（模板）";
    }

    @Override
    public int order() {
        return 999;
    }

    @Override
    public String apiPathPrefix() {
        return "/api/v1/admin/demo";
    }

    @Override
    public String uiRouteHash() {
        return "#/demo";
    }

    @Override
    public boolean enabledByDefault() {
        return false;
    }

    @Override
    public AdminModuleManifest manifest() {
        return new AdminModuleManifest(moduleId(), displayName(), order(), apiPathPrefix(), uiRouteHash(),
                false, "/static/admin/modules/demo.js");
    }
}

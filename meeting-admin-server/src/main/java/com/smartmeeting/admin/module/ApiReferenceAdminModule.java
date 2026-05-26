package com.smartmeeting.admin.module;

import com.smartmeeting.config.admin.AdminModule;
import com.smartmeeting.config.admin.AdminModuleManifest;
import org.springframework.stereotype.Component;

@Component
public class ApiReferenceAdminModule implements AdminModule {

    @Override
    public String moduleId() {
        return "api-reference";
    }

    @Override
    public String displayName() {
        return "数据更新 API";
    }

    @Override
    public int order() {
        return 5;
    }

    @Override
    public String apiPathPrefix() {
        return "/api/v1/admin/api-reference";
    }

    @Override
    public String uiRouteHash() {
        return "#/api-reference";
    }

    @Override
    public AdminModuleManifest manifest() {
        return new AdminModuleManifest(moduleId(), displayName(), order(), apiPathPrefix(), uiRouteHash(),
                true, "/static/admin/modules/api-reference.js");
    }
}

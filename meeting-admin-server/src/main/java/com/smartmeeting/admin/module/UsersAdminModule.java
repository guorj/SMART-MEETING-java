package com.smartmeeting.admin.module;

import com.smartmeeting.config.admin.AdminModule;
import com.smartmeeting.config.admin.AdminModuleManifest;
import org.springframework.stereotype.Component;

@Component
public class UsersAdminModule implements AdminModule {

    @Override
    public String moduleId() {
        return "users";
    }

    @Override
    public String displayName() {
        return "用户管理";
    }

    @Override
    public int order() {
        return 27;
    }

    @Override
    public String apiPathPrefix() {
        return "/api/v1/admin/users";
    }

    @Override
    public String uiRouteHash() {
        return "#/users";
    }

    @Override
    public AdminModuleManifest manifest() {
        return new AdminModuleManifest(moduleId(), displayName(), order(), apiPathPrefix(), uiRouteHash(),
                true, "/static/admin/modules/users.js");
    }
}

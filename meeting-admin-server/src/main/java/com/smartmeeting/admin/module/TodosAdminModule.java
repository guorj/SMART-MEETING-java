package com.smartmeeting.admin.module;

import com.smartmeeting.config.admin.AdminModule;
import com.smartmeeting.config.admin.AdminModuleManifest;
import org.springframework.stereotype.Component;

@Component
public class TodosAdminModule implements AdminModule {
    @Override
    public String moduleId() {
        return "todos";
    }

    @Override
    public String displayName() {
        return "待办追踪";
    }

    @Override
    public int order() {
        return 32;
    }

    @Override
    public String apiPathPrefix() {
        return "/api/v1/admin/todos";
    }

    @Override
    public String uiRouteHash() {
        return "#/todos";
    }

    @Override
    public AdminModuleManifest manifest() {
        return new AdminModuleManifest(moduleId(), displayName(), order(), apiPathPrefix(), uiRouteHash(),
                true, "/static/admin/modules/todos.js");
    }
}

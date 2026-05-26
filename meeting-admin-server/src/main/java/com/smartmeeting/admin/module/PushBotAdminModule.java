package com.smartmeeting.admin.module;

import com.smartmeeting.config.admin.AdminModule;
import com.smartmeeting.config.admin.AdminModuleManifest;
import org.springframework.stereotype.Component;

@Component
public class PushBotAdminModule implements AdminModule {

    @Override
    public String moduleId() {
        return "push-bot";
    }

    @Override
    public String displayName() {
        return "推送调度";
    }

    @Override
    public int order() {
        return 25;
    }

    @Override
    public String apiPathPrefix() {
        return "/api/v1/admin/push-tasks";
    }

    @Override
    public String uiRouteHash() {
        return "#/push-bot";
    }

    @Override
    public AdminModuleManifest manifest() {
        return new AdminModuleManifest(moduleId(), displayName(), order(), apiPathPrefix(), uiRouteHash(),
                true, "/static/admin/modules/push-bot.js");
    }
}

package com.smartmeeting.admin.module;

import com.smartmeeting.config.admin.AdminModule;
import com.smartmeeting.config.admin.AdminModuleManifest;
import org.springframework.stereotype.Component;

@Component
public class EventOutboxAdminModule implements AdminModule {
    @Override
    public String moduleId() {
        return "event-outbox";
    }

    @Override
    public String displayName() {
        return "事件投递";
    }

    @Override
    public int order() {
        return 52;
    }

    @Override
    public String apiPathPrefix() {
        return "/api/v1/admin/event-outbox";
    }

    @Override
    public String uiRouteHash() {
        return "#/event-outbox";
    }

    @Override
    public AdminModuleManifest manifest() {
        return new AdminModuleManifest(moduleId(), displayName(), order(), apiPathPrefix(), uiRouteHash(),
                true, "/static/admin/modules/event-outbox.js");
    }
}

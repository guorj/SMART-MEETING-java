package com.smartmeeting.admin.module;

import com.smartmeeting.config.admin.AdminModule;
import com.smartmeeting.config.admin.AdminModuleManifest;
import org.springframework.stereotype.Component;

@Component
public class MeetingsAdminModule implements AdminModule {

    @Override
    public String moduleId() {
        return "meetings";
    }

    @Override
    public String displayName() {
        return "会议运维";
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public String apiPathPrefix() {
        return "/api/v1/admin/meetings";
    }

    @Override
    public String uiRouteHash() {
        return "#/meetings";
    }

    @Override
    public AdminModuleManifest manifest() {
        return new AdminModuleManifest(moduleId(), displayName(), order(), apiPathPrefix(), uiRouteHash(),
                true, "/static/admin/modules/meetings.js");
    }
}

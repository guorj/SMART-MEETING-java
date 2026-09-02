package com.smartmeeting.admin.module;

import com.smartmeeting.config.admin.AdminModule;
import com.smartmeeting.config.admin.AdminModuleManifest;
import org.springframework.stereotype.Component;

@Component
public class MinuteSkillsAdminModule implements AdminModule {

    @Override
    public String moduleId() {
        return "minute-skills";
    }

    @Override
    public String displayName() {
        return "纪要 Skill";
    }

    @Override
    public int order() {
        return 15;
    }

    @Override
    public String apiPathPrefix() {
        return "/api/v1/admin/minute-skills";
    }

    @Override
    public String uiRouteHash() {
        return "#/minute-skills";
    }

    @Override
    public AdminModuleManifest manifest() {
        return new AdminModuleManifest(moduleId(), displayName(), order(), apiPathPrefix(), uiRouteHash(),
                true, "/static/admin/modules/minute-skills.js");
    }
}

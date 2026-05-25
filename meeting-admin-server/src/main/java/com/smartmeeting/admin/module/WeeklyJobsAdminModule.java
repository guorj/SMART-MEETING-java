package com.smartmeeting.admin.module;

import com.smartmeeting.config.admin.AdminModule;
import com.smartmeeting.config.admin.AdminModuleManifest;
import org.springframework.stereotype.Component;

@Component
public class WeeklyJobsAdminModule implements AdminModule {

    @Override
    public String moduleId() {
        return "weekly-jobs";
    }

    @Override
    public String displayName() {
        return "对比任务";
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public String apiPathPrefix() {
        return "/api/v1/admin/weekly-jobs";
    }

    @Override
    public String uiRouteHash() {
        return "#/weekly-jobs";
    }

    @Override
    public AdminModuleManifest manifest() {
        return new AdminModuleManifest(moduleId(), displayName(), order(), apiPathPrefix(), uiRouteHash(),
                true, "/static/admin/modules/weekly-jobs.js");
    }
}

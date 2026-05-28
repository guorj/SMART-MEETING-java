package com.smartmeeting.admin.module;

import com.smartmeeting.config.admin.AdminModule;
import com.smartmeeting.config.admin.AdminModuleManifest;
import org.springframework.stereotype.Component;

@Component
public class PipelineAdminModule implements AdminModule {
    @Override
    public String moduleId() {
        return "pipeline";
    }

    @Override
    public String displayName() {
        return "流水线编排";
    }

    @Override
    public int order() {
        return 35;
    }

    @Override
    public String apiPathPrefix() {
        return "/api/v1/admin/pipeline";
    }

    @Override
    public String uiRouteHash() {
        return "#/pipeline";
    }

    @Override
    public AdminModuleManifest manifest() {
        return new AdminModuleManifest(moduleId(), displayName(), order(), apiPathPrefix(), uiRouteHash(),
                true, "/static/admin/modules/pipeline.js");
    }
}

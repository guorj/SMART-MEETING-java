package com.smartmeeting.config.admin;

/**
 * 管理后台业务模块插件（L1）。
 */
public interface AdminModule {

    String moduleId();

    String displayName();

    int order();

    String apiPathPrefix();

    String uiRouteHash();

    default boolean enabledByDefault() {
        return true;
    }

    AdminModuleManifest manifest();
}

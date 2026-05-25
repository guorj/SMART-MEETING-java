package com.smartmeeting.config.admin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminModuleManifest {
    private String moduleId;
    private String displayName;
    private int order;
    private String apiPathPrefix;
    private String uiRouteHash;
    private boolean enabled;
    private String scriptPath;
}

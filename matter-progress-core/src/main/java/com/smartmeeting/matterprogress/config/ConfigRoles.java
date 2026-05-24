package com.smartmeeting.matterprogress.config;

/** config_role 常量 */
public final class ConfigRoles {

    public static final String SOURCE = "SOURCE";
    public static final String OUTPUT = "OUTPUT";
    public static final String BOTH = "BOTH";

    private ConfigRoles() {
    }

    /** 是否参与会序 SOURCE 资料合并（meeting-server） */
    public static boolean isSourceForMerge(String role) {
        if (role == null || role.isBlank()) {
            return true;
        }
        String r = role.trim().toUpperCase();
        return SOURCE.equals(r) || BOTH.equals(r);
    }

    /** 是否承载 bot 写回的 generated_report_url */
    public static boolean isReportSink(String role) {
        if (role == null || role.isBlank()) {
            return false;
        }
        String r = role.trim().toUpperCase();
        return OUTPUT.equals(r) || BOTH.equals(r);
    }
}

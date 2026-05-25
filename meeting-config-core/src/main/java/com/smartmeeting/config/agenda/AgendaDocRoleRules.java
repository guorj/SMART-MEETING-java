package com.smartmeeting.config.agenda;

import java.util.Locale;

public final class AgendaDocRoleRules {

    private AgendaDocRoleRules() {
    }

    /** 合并 host 会序飞书外链时仅 SOURCE/BOTH。 */
    public static boolean isSourceRoleForMerge(AgendaDocBindingSnapshot cfg) {
        if (cfg == null) {
            return false;
        }
        String role = cfg.getConfigRole();
        if (role == null || role.isBlank()) {
            return true;
        }
        role = role.trim().toUpperCase(Locale.ROOT);
        return "SOURCE".equals(role) || "BOTH".equals(role);
    }
}

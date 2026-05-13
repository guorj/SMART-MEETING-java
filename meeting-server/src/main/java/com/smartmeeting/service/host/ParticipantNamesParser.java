package com.smartmeeting.service.host;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 解析 int_meeting_type_preset.participants_names（顿号、逗号、分号、空白、「及」等分隔）。
 */
public final class ParticipantNamesParser {

    private ParticipantNamesParser() {
    }

    public static List<String> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String normalized = raw.replace('及', ',').replace('、', ',');
        String[] parts = normalized.split("[,，;；\\s]+");
        Set<String> seen = new LinkedHashSet<>();
        for (String p : parts) {
            String t = p == null ? "" : p.trim();
            if (!t.isEmpty()) {
                seen.add(t);
            }
        }
        return new ArrayList<>(seen);
    }
}

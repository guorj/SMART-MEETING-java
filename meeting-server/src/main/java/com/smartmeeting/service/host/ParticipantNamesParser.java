package com.smartmeeting.service.host;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 会议预设参会人姓名字符串解析器。
 *
 * <p>解析 {@code int_meeting_type_preset.participants_names} 等字段：
 * 支持顿号、逗号、分号、空白及「及」等分隔，去重并保持首次出现顺序。
 *
 * <p>与 {@link com.smartmeeting.service.MeetingTypePresetService#splitNames(String)} 规则相近，
 * 本类侧重主持/点名场景的去重列表输出。
 */
public final class ParticipantNamesParser {

    private ParticipantNamesParser() {
    }

    /**
     * 将原始姓名字符串解析为去重后的姓名列表。
     *
     * @param raw 原始字符串，可为 null
     * @return 姓名列表；null 或空白输入返回不可变空列表
     */
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

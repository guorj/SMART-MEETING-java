package com.smartmeeting.matterprogress.openclaw;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 构建 OpenClaw Skill 模式 prompt：{@code /skill:xxx\nkey=value\n...}
 */
public final class OpenClawSkillPromptBuilder {

    private OpenClawSkillPromptBuilder() {
    }

    public static String build(String skillName, Consumer<LinkedHashMap<String, String>> dataConsumer) {
        LinkedHashMap<String, String> data = new LinkedHashMap<>();
        dataConsumer.accept(data);
        return build(skillName, data);
    }

    public static String build(String skillName, LinkedHashMap<String, String> data) {
        StringBuilder sb = new StringBuilder();
        sb.append("/skill:").append(skillName);
        for (Map.Entry<String, String> entry : data.entrySet()) {
            sb.append("\n").append(entry.getKey()).append("=").append(entry.getValue());
        }
        return sb.toString();
    }
}

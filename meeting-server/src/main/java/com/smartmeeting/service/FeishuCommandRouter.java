package com.smartmeeting.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 飞书指令路由器（按插入顺序匹配，先写更具体的模式）
 */
@Slf4j
@Service
public class FeishuCommandRouter {

    private static final Map<Pattern, String> COMMAND_PATTERNS = new LinkedHashMap<>();

    static {
        // 显式查看完整指令（混合方案：未知指令不再自动推送长文）
        COMMAND_PATTERNS.put(
                Pattern.compile("^(?:帮助|指令|帮助信息|help)\\s*$", Pattern.CASE_INSENSITIVE),
                "show_help");
        COMMAND_PATTERNS.put(Pattern.compile("^开始会议\\s+([1-5])\\s*$"), "start_meeting_preset");
        COMMAND_PATTERNS.put(Pattern.compile("^开始会议\\s+6\\s+主题[:：]\\s*(.+)\\s*$"), "start_meeting_other_with_title");
        COMMAND_PATTERNS.put(Pattern.compile("^开始会议\\s+6\\s*$"), "start_meeting_other_prompt");
        COMMAND_PATTERNS.put(Pattern.compile("^会议类型\\s*([1-6])\\s*$"), "meeting_type_short");
        COMMAND_PATTERNS.put(
                Pattern.compile("^开始会议\\s+主题[:：]\\s*(.+?)(?:\\s+参会人[:：]\\s*(.+))?$"),
                "start_meeting");
        COMMAND_PATTERNS.put(Pattern.compile("^开始会议\\s*$"), "start_meeting_menu");
        COMMAND_PATTERNS.put(
                Pattern.compile("^结束会议(?:\\s+会议ID[:：]\\s*([a-f0-9\\-]+))?$"),
                "stop_meeting"
        );
        COMMAND_PATTERNS.put(
                Pattern.compile("^注册声纹(?:\\s+姓名[:：]\\s*(.+))?$"),
                "register_voiceprint"
        );
        COMMAND_PATTERNS.put(
                Pattern.compile("^查看纪要(?:\\s+最近(\\d+)条)?$"),
                "list_minutes"
        );
        COMMAND_PATTERNS.put(
                Pattern.compile("^修改说话人\\s+([a-f0-9\\-]+)\\s+(\\S+?)[:：]\\s*(.+)$"),
                "rename_speaker"
        );
        COMMAND_PATTERNS.put(
                Pattern.compile("^重新生成(?:\\s+会议ID[:：]\\s*([a-f0-9\\-]+))?$"),
                "regenerate_minutes"
        );
        COMMAND_PATTERNS.put(
                Pattern.compile("^加入会议(?:\\s+会议ID[:：]\\s*([a-f0-9\\-]+))?$"),
                "join_meeting"
        );
    }

    public CommandResult parse(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new CommandResult("unknown", Map.of());
        }

        text = text.trim();

        for (Map.Entry<Pattern, String> entry : COMMAND_PATTERNS.entrySet()) {
            Matcher matcher = entry.getKey().matcher(text);
            if (matcher.matches()) {
                String command = entry.getValue();
                Map<String, String> params = extractParams(matcher, command);
                log.info("指令解析: command={}, params={}", command, params);
                return new CommandResult(command, params);
            }
        }

        log.debug("未识别的指令: {}", text);
        return new CommandResult("unknown", Map.of("text", text));
    }

    private Map<String, String> extractParams(Matcher matcher, String command) {
        Map<String, String> params = new HashMap<>();

        switch (command) {
            case "show_help":
                break;
            case "start_meeting_preset":
                params.put("type", matcher.group(1));
                break;
            case "start_meeting_other_with_title":
                params.put("title", matcher.group(1).trim());
                break;
            case "meeting_type_short":
                params.put("num", matcher.group(1));
                break;
            case "start_meeting":
                params.put("title", matcher.group(1) != null ? matcher.group(1).trim() : "未命名会议");
                params.put("participants", matcher.group(2) != null ? matcher.group(2).trim() : "");
                break;
            case "stop_meeting":
                params.put("meeting_id", matcher.group(1) != null ? matcher.group(1) : "");
                break;
            case "register_voiceprint":
                params.put("name", matcher.group(1) != null ? matcher.group(1) : "");
                break;
            case "list_minutes":
                params.put("limit", matcher.group(1) != null ? matcher.group(1) : "5");
                break;
            case "rename_speaker":
                params.put("meeting_id", matcher.group(1));
                params.put("old_name", matcher.group(2));
                params.put("new_name", matcher.group(3));
                break;
            case "regenerate_minutes":
                params.put("meeting_id", matcher.group(1) != null ? matcher.group(1) : "");
                break;
            case "join_meeting":
                params.put("meeting_id", matcher.group(1) != null ? matcher.group(1) : "");
                break;
            default:
                break;
        }

        return params;
    }

    public String getHelpText() {
        return "📖 可用指令：\n"
                + "• 开始会议 — 打开会务页选类型，或发「开始会议 1」等\n"
                + "• 开始会议 1 … 开始会议 5 — 按类型创建（吉青固定会务）\n"
                + "• 开始会议 6 — 其他会议，再回复主题或「主题:xxx」\n"
                + "• 开始会议 6 主题:xxx — 直接创建其他会议\n"
                + "• 会议类型 1 … 会议类型 6 — 同上快捷方式\n"
                + "• 开始会议 主题:xxx 参会人:张三,李四 — 完全自定义\n"
                + "• 结束会议 — 请在录音页点「结束会议」；飞书发「结束会议」为备用\n"
                + "• 加入会议 会议ID:uuid\n"
                + "• 注册声纹 姓名:张三\n"
                + "• 查看纪要 / 查看纪要 最近10条\n"
                + "• 修改说话人 uuid 参会人A:张三\n"
                + "• 重新生成 会议ID:uuid";
    }

    public static class CommandResult {
        private final String command;
        private final Map<String, String> params;

        public CommandResult(String command, Map<String, String> params) {
            this.command = command;
            this.params = params;
        }

        public String getCommand() {
            return command;
        }

        public Map<String, String> getParams() {
            return params;
        }

        public String getParam(String key) {
            return params.get(key);
        }

        public String getParam(String key, String defaultValue) {
            return params.getOrDefault(key, defaultValue);
        }
    }
}

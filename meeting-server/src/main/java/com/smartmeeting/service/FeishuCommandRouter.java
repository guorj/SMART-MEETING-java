package com.smartmeeting.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 飞书文本指令路由器。
 *
 * <p>按 {@link #COMMAND_PATTERNS} 的插入顺序匹配用户消息，将自然语言指令解析为
 * 命令名与捕获组参数，供 {@link FeishuCommandHandler} 分发执行。更具体的模式应写在前面。
 */
@Slf4j
@Service
public class FeishuCommandRouter {

    private static final Map<Pattern, String> COMMAND_PATTERNS = new LinkedHashMap<>();

    static {
        COMMAND_PATTERNS.put(
                Pattern.compile("^(?:帮助|指令|帮助信息|help)\\s*$", Pattern.CASE_INSENSITIVE),
                "show_help");
        COMMAND_PATTERNS.put(Pattern.compile("^会议管理\\s*$"), "open_dashboard");
        COMMAND_PATTERNS.put(
                Pattern.compile("^结束会议(?:\\s+会议ID[:：]\\s*([a-f0-9\\-]+))?$"),
                "stop_meeting"
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

    /**
     * 解析用户发送的飞书文本为结构化指令。
     *
     * @param text 用户原始消息（前后空白会被 trim）
     * @return 匹配成功时含命令名与参数；空消息或未匹配时命令为 {@code unknown}
     */
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

    /**
     * 根据命令类型从正则匹配结果提取命名参数。
     *
     * @param matcher 已 {@code matches()} 的匹配器
     * @param command 命令标识（如 {@code start_meeting}）
     * @return 参数键值对，无参数时返回空 Map
     */
    private Map<String, String> extractParams(Matcher matcher, String command) {
        Map<String, String> params = new HashMap<>();

        switch (command) {
            case "show_help":
                break;
            case "open_dashboard":
                break;
            case "stop_meeting":
                params.put("meeting_id", matcher.group(1) != null ? matcher.group(1) : "");
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

    /**
     * 返回完整飞书 Bot 指令帮助文案（用户主动发送「帮助」时使用）。
     *
     * @return 多行 Markdown 风格说明文本
     */
    public String getHelpText() {
        return "📖 可用指令：\n"
                + "• 会议管理 — 打开统一会议前台（开始会议/注册声纹/查看纪要）\n"
                + "• 结束会议 — 请在录音页点「结束会议」；飞书发「结束会议」为备用\n"
                + "• 修改说话人 uuid 参会人A:张三\n"
                + "• 重新生成 会议ID:uuid";
    }

    /**
     * 指令解析结果：命令名 + 正则捕获参数。
     *
     * @param command 命令标识，未识别时为 {@code unknown}
     * @param params  命名参数（如 {@code meeting_id}、{@code title}）
     */
    public static class CommandResult {
        private final String command;
        private final Map<String, String> params;

        /**
         * @param command 命令标识
         * @param params  参数 Map，不可为 {@code null}
         */
        public CommandResult(String command, Map<String, String> params) {
            this.command = command;
            this.params = params;
        }

        /** @return 命令标识 */
        public String getCommand() {
            return command;
        }

        /** @return 不可变的参数视图（实际为构造时传入的 Map） */
        public Map<String, String> getParams() {
            return params;
        }

        /**
         * @param key 参数名
         * @return 参数值，不存在时返回 {@code null}
         */
        public String getParam(String key) {
            return params.get(key);
        }

        /**
         * @param key          参数名
         * @param defaultValue 缺省值
         * @return 参数值或缺省值
         */
        public String getParam(String key, String defaultValue) {
            return params.getOrDefault(key, defaultValue);
        }
    }
}

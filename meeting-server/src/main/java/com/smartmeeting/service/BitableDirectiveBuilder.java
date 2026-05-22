package com.smartmeeting.service;

import com.smartmeeting.entity.Meeting;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 综合管理会场景的飞书多维表格指令构建器。
 *
 * <p>当会议预设类型命中配置项 {@code openclaw.comprehensive-bitable-preset-codes} 且功能开关开启时，
 * 构建「读取指定多维表」的指令文本，供 {@link com.smartmeeting.service.agent.AgentProvider} 使用：
 * <ul>
 *   <li>会开始 — 上次待办进度 JSON 分析（{@link #buildDirectiveForPreviousMeetingProgress}）</li>
 *   <li>主持会序 — OpenClaw 会序通报 Markdown（{@link #buildDirectiveForHostAgenda}）</li>
 * </ul>
 *
 * <p>默认多维表 URL 见 {@link #DEFAULT_COMPREHENSIVE_BITABLE_URL}；
 * 配置 {@code openclaw.comprehensive-bitable-url} 非空时覆盖。
 *
 * <p>此类不直接调用 OpenClaw，仅负责指令文本的构建。Provider 实现决定如何使用这些指令。
 */
@Slf4j
@Component
public class BitableDirectiveBuilder {

    /** 综合管理事项代办清单 — 未配置 {@code openclaw.comprehensive-bitable-url} 时使用 */
    public static final String DEFAULT_COMPREHENSIVE_BITABLE_URL =
            "https://ovjde0k7vc1.feishu.cn/base/PjL2b6sPBa9UESsVhhJcMbwSnmc?table=tblFH8QdCzw1RK3m&view=vewM1Y9Vem";

    @Value("${openclaw.comprehensive-bitable-progress-enabled:false}")
    private boolean enabled;

    @Value("${openclaw.comprehensive-bitable-preset-codes:1}")
    private String presetCodesCsv;

    @Value("${openclaw.comprehensive-bitable-display-name:📋综合管理事项代办清单 测试}")
    private String displayName;

    @Value("${openclaw.comprehensive-bitable-url:}")
    private String bitableUrl;

    /**
     * 判断当前会议是否适用综合管理会多维表策略。
     *
     * @param meeting 会议实体（含预设类型编码）
     * @return 功能已启用、展示名非空且预设编码匹配时为 {@code true}
     */
    public boolean appliesTo(Meeting meeting) {
        if (!enabled || meeting == null || meeting.getPresetTypeCode() == null) {
            return false;
        }
        String name = displayName != null ? displayName.trim() : "";
        if (name.isEmpty()) {
            return false;
        }
        return presetMatches(meeting.getPresetTypeCode());
    }

    /**
     * 构建「会开始 → 上次待办进度」场景的多维表指令（输出仍为 JSON）。
     *
     * @param current 当前会议
     * @return 多维表必读指令；不适用时返回 {@code null}
     */
    public String buildDirectiveForPreviousMeetingProgress(Meeting current) {
        if (!appliesTo(current)) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("【飞书多维表格-会前必读】当前会议命中「综合管理会」会前进度通报策略。\n");
        sb.append("请读取飞书多维表格 **「").append(displayName.trim()).append("」** 的当前行数据，");
        sb.append("据此撰写进度、风险与本次会议关注点，并与下方「系统 int_meeting_todo 统计」交叉说明。\n");
        appendUrlHint(sb);
        sb.append("输出格式仍为任务末尾要求的 **唯一 JSON**；若无法读取该多维表格，progress_summary 首句必须写「未读取到飞书多维表格」。");
        log.info("Bitable directive: previous-progress meetingId={}, preset={}",
                current.getId(), current.getPresetTypeCode());
        return sb.toString();
    }

    /**
     * 构建「主持会序 → OpenClaw 会序通报」场景指令：读取指定会序飞书资料 URL，输出 Markdown 通报。
     *
     * @param agendaTitle 会序标题（展示用）
     * @param feishuUrl   当前会序飞书链接（docx/wiki/base）
     * @param feishuKind  资源类型 BASE / DOCX / WIKI 等，可为空
     * @return 指令正文；URL 无效时返回 {@code null}
     */
    public String buildDirectiveForHostAgenda(String agendaTitle, String feishuUrl, String feishuKind) {
        String url = feishuUrl != null ? feishuUrl.trim() : "";
        if (url.isEmpty()) {
            return null;
        }
        String title = agendaTitle != null ? agendaTitle.trim() : "当前会序";
        String kind = feishuKind != null && !feishuKind.isBlank() ? feishuKind.trim() : "UNKNOWN";
        StringBuilder sb = new StringBuilder();
        sb.append("【飞书资料-主持会序通报】请读取以下会序绑定的飞书资料并生成「事项进度通报」Markdown。\n");
        sb.append("- 会序标题：**").append(title).append("**\n");
        sb.append("- 资料类型：").append(kind).append("\n");
        sb.append("资料直达链接：").append(url).append("\n");
        sb.append("**仅输出 Markdown 正文**（含分级标题、表格或列表），突出概览、分项进度、风险与建议；不要使用 JSON 代码块包裹全文。\n");
        sb.append("若无法读取该资料，正文开头单独一行写：「未读取到飞书资料。」并简述原因。");
        log.info("Bitable directive: host agenda briefing title={}, kind={}", title, kind);
        return sb.toString();
    }

    /** 在指令末尾追加多维表直达链接提示。 */
    private void appendUrlHint(StringBuilder sb) {
        sb.append("表格直达链接：").append(resolveBitableUrl()).append("\n");
    }

    /** 解析生效的多维表 URL（配置优先，否则默认常量）。 */
    private String resolveBitableUrl() {
        String u = bitableUrl != null ? bitableUrl.trim() : "";
        return u.isEmpty() ? DEFAULT_COMPREHENSIVE_BITABLE_URL : u;
    }

    /**
     * 判断会议预设类型编码是否在配置的 CSV 白名单内。
     */
    private boolean presetMatches(int presetTypeCode) {
        if (presetCodesCsv == null || presetCodesCsv.isBlank()) {
            return false;
        }
        for (String part : presetCodesCsv.split(",")) {
            String t = part.trim();
            if (t.isEmpty()) {
                continue;
            }
            try {
                if (presetTypeCode == Integer.parseInt(t)) {
                    return true;
                }
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
        return false;
    }
}

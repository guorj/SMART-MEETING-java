package com.smartmeeting.service;

import com.smartmeeting.entity.Meeting;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 综合管理会场景的 OpenClaw 飞书多维表格临时策略分支。
 *
 * <p>当会议预设类型命中配置项 {@code openclaw.comprehensive-bitable-preset-codes} 且功能开关开启时，
 * 向 {@link AiAgentService} 注入「读取指定多维表」指令，用于：
 * <ul>
 *   <li>会开始 — 上次待办进度 JSON 分析（{@link #buildDirectiveForPreviousMeetingProgress}）</li>
 *   <li>录音页 — 事项进度通报 Markdown（{@link #buildDirectiveForRecordingMatterProgress}）</li>
 * </ul>
 *
 * <p>默认多维表 URL 见 {@link #DEFAULT_COMPREHENSIVE_BITABLE_URL}；
 * 配置 {@code openclaw.comprehensive-bitable-url} 非空时覆盖。
 */
@Slf4j
@Component
public class OpenclawComprehensiveBitableBranch {

    /** 综合管理事项代办清单（测试）— 未配置 {@code openclaw.comprehensive-bitable-url} 时使用 */
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
     * 构建「会开始 → 上次待办进度」场景的 OpenClaw 指令（输出仍为 JSON）。
     *
     * @param current 当前会议
     * @return 多维表必读指令；不适用时返回 {@code null}
     */
    public String buildDirectiveForPreviousMeetingProgress(Meeting current) {
        if (!appliesTo(current)) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("【飞书多维表格-会前必读】当前会议命中「综合管理会」会前进度通报临时策略。\n");
        sb.append("请你通过 **OpenClaw CLI / 会话内可用工具** 读取飞书多维表格 **「").append(displayName.trim()).append("」** 的当前行数据，");
        sb.append("据此撰写进度、风险与本次会议关注点，并与下方「系统 int_meeting_todo 统计」交叉说明。\n");
        appendUrlHint(sb);
        sb.append("输出格式仍为任务末尾要求的 **唯一 JSON**；若无法读取该多维表格，progress_summary 首句必须写「未读取到飞书多维表格」。");
        log.info("OpenClaw comprehensive bitable: previous-progress directive meetingId={}, preset={}",
                current.getId(), current.getPresetTypeCode());
        return sb.toString();
    }

    /**
     * 构建「录音页 → 事项进度通报」场景的 OpenClaw 指令（仅输出 Markdown 正文）。
     *
     * @param current 当前会议
     * @return 多维表必读指令；不适用时返回 {@code null}
     */
    public String buildDirectiveForRecordingMatterProgress(Meeting current) {
        if (!appliesTo(current)) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("【飞书多维表格-录音页事项进度通报】当前会议命中综合管理会临时策略。\n");
        sb.append("请你通过 **OpenClaw CLI / 会话内可用工具** 读取飞书多维表格 **「").append(displayName.trim()).append("」** 的当前行数据，");
        sb.append("生成「事项进度通报」：需含概览、分项进度（列表或表格）、风险与需协调事项、下一步建议；语言简洁专业。 不含数据源信息，统计时间精确到小时级\n");
        appendUrlHint(sb);
        sb.append("**仅输出 Markdown 正文**，不要使用 JSON 代码块包裹全文；不要输出除通报外的闲聊。\n");
        sb.append("若无法读取该多维表格，正文开头单独一行写：「未读取到飞书多维表格。」其后可简述原因并列出你仍能从会话中推断的要点（若有）。");
        log.info("OpenClaw comprehensive bitable: recording matter-progress directive meetingId={}, preset={}",
                current.getId(), current.getPresetTypeCode());
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
     *
     * @param presetTypeCode 会议预设类型编码
     * @return 匹配任一项时为 {@code true}
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

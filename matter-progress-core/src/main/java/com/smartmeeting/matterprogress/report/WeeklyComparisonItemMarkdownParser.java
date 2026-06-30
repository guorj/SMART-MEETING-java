package com.smartmeeting.matterprogress.report;

import com.smartmeeting.matterprogress.model.ParsedComparisonItems;
import com.smartmeeting.matterprogress.model.WeeklyComparisonItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Legacy 路径：从 LLM 产出的 Markdown（三组 `##` + 事项行）解析为 items。
 * <p>解析失败（无三组标题或 0 行）→ failed，run 标 FAILED（不写 READY+0 条）。
 * 行格式：`- 事项：…，责任人：…，时间节点：…，状态：…`
 */
public class WeeklyComparisonItemMarkdownParser {

    private static final Logger log = LoggerFactory.getLogger(WeeklyComparisonItemMarkdownParser.class);

    private static final Pattern ITEM_LINE = Pattern.compile(
            "^[-*]\\s*事项[：:]\\s*(?<matter>.+?)\\s*[,，]\\s*责任人[：:]\\s*(?<assignee>[^,，]*)\\s*[,，]\\s*时间节点[：:]\\s*(?<time>[^,，]*)\\s*[,，]\\s*状态[：:]\\s*(?<status>[^,，\\s]+)");

    public ParsedComparisonItems parse(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return ParsedComparisonItems.failed("Markdown 为空");
        }
        // 检查三组标题存在
        if (!markdown.contains("## 延期事项") && !markdown.contains("## 已完成事项")
                && !markdown.contains("## 进行中事项")) {
            return ParsedComparisonItems.failed("Markdown 缺少三组 `##` 标题");
        }
        List<WeeklyComparisonItem> items = new ArrayList<>();
        int discarded = 0;
        String currentCategory = null;
        int sortOrder = 0;
        for (String line : markdown.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("## 延期事项")) {
                currentCategory = WeeklyComparisonItem.DELAYED;
                sortOrder = 0;
                continue;
            }
            if (trimmed.startsWith("## 已完成事项")) {
                currentCategory = WeeklyComparisonItem.COMPLETED;
                sortOrder = 0;
                continue;
            }
            if (trimmed.startsWith("## 进行中事项")) {
                currentCategory = WeeklyComparisonItem.IN_PROGRESS;
                sortOrder = 0;
                continue;
            }
            if (currentCategory == null) {
                continue;
            }
            if (trimmed.startsWith("- 无") || trimmed.startsWith("* 无")) {
                continue;
            }
            Matcher m = ITEM_LINE.matcher(trimmed);
            if (!m.find()) {
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    discarded++;
                }
                continue;
            }
            String matterName = m.group("matter").trim();
            String assigneeRaw = m.group("assignee").trim();
            String timeNode = m.group("time").trim();
            String statusLabel = m.group("status").trim();
            String assignee = assigneeRaw.isEmpty() || "未提及".equals(assigneeRaw) ? null : assigneeRaw;
            String time = timeNode.isEmpty() || "未提及".equals(timeNode) ? null : timeNode;
            WeeklyComparisonItem item = new WeeklyComparisonItem(
                    currentCategory, matterName, assignee, time, statusLabel, sortOrder++, null);
            if (!item.isCategoryConsistent()) {
                log.warn("丢弃事项 statusLabel/category 不一致: category={} statusLabel={}",
                        item.category(), item.statusLabel());
                discarded++;
                continue;
            }
            items.add(item);
        }
        if (items.isEmpty() && discarded == 0) {
            return ParsedComparisonItems.failed("Markdown 解析出 0 条事项（无有效行）");
        }
        return ParsedComparisonItems.ok(items, discarded);
    }
}

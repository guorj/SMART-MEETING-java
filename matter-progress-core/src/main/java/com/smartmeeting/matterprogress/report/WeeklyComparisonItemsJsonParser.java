package com.smartmeeting.matterprogress.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.matterprogress.model.ParsedComparisonItems;
import com.smartmeeting.matterprogress.model.WeeklyComparisonItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从 Agent 回复中提取 BEGIN/END_WEEKLY_COMPARISON_ITEMS 包裹的 JSON 块并解析为事项列表。
 * <p>校验：category 枚举、statusLabel↔category 一致、matterName 必填；不合格的 item 丢弃并计数。
 */
public class WeeklyComparisonItemsJsonParser {

    private static final Logger log = LoggerFactory.getLogger(WeeklyComparisonItemsJsonParser.class);

    private static final Pattern BLOCK = Pattern.compile(
            "-----BEGIN_WEEKLY_COMPARISON_ITEMS-----\\s*(\\{.*?})\\s*-----END_WEEKLY_COMPARISON_ITEMS-----",
            Pattern.DOTALL);

    private static final Set<String> VALID_CATEGORIES = Set.of(
            WeeklyComparisonItem.DELAYED, WeeklyComparisonItem.COMPLETED, WeeklyComparisonItem.IN_PROGRESS);

    private final ObjectMapper objectMapper;

    public WeeklyComparisonItemsJsonParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * @param replyText Agent 完整回复
     * @return 解析结果；找不到块或 JSON 解析失败 → failed
     */
    public ParsedComparisonItems parse(String replyText) {
        if (replyText == null || replyText.isBlank()) {
            return ParsedComparisonItems.failed("Agent 回复为空");
        }
        Matcher m = BLOCK.matcher(replyText);
        if (!m.find()) {
            return ParsedComparisonItems.failed("未找到 BEGIN/END_WEEKLY_COMPARISON_ITEMS 块");
        }
        String json = m.group(1);
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode itemsNode = root.path("items");
            if (!itemsNode.isArray()) {
                return ParsedComparisonItems.failed("items 不是数组");
            }
            List<WeeklyComparisonItem> kept = new ArrayList<>();
            int discarded = 0;
            for (JsonNode n : itemsNode) {
                WeeklyComparisonItem item = parseItem(n);
                if (item == null) {
                    discarded++;
                    continue;
                }
                if (!item.isCategoryConsistent()) {
                    log.warn("丢弃事项 statusLabel/category 不一致: category={} statusLabel={}",
                            item.category(), item.statusLabel());
                    discarded++;
                    continue;
                }
                kept.add(item);
            }
            long runId = root.path("runId").asLong(0);
            if (runId > 0) {
                return ParsedComparisonItems.okWithRunId(kept, discarded, runId);
            }
            return ParsedComparisonItems.ok(kept, discarded);
        } catch (Exception e) {
            return ParsedComparisonItems.failed("JSON 解析失败: " + e.getMessage());
        }
    }

    static WeeklyComparisonItem parseItem(JsonNode n) {
        String category = text(n, "category");
        String matterName = text(n, "matterName");
        if (matterName == null || matterName.isBlank()) {
            matterName = text(n, "matter_name");
        }
        if (category == null || !VALID_CATEGORIES.contains(category)) {
            return null;
        }
        if (matterName == null || matterName.isBlank()) {
            return null;
        }
        String assignee = nullableText(n, "assignee");
        String timeNode = nullableText(n, "timeNode");
        if (timeNode == null) {
            timeNode = nullableText(n, "time_node");
        }
        String statusLabel = text(n, "statusLabel");
        if (statusLabel == null) {
            statusLabel = text(n, "status_label");
        }
        if (statusLabel == null || statusLabel.isBlank()) {
            return null;
        }
        int sortOrder = n.path("sortOrder").asInt(n.path("sort_order").asInt(0));
        String sourceConfig = nullableText(n, "sourceConfigName");
        if (sourceConfig == null) {
            sourceConfig = nullableText(n, "source_config_name");
        }
        return new WeeklyComparisonItem(category, matterName.trim(), assignee, timeNode,
                statusLabel.trim(), Math.max(0, sortOrder), sourceConfig);
    }

    private static String text(JsonNode n, String field) {
        String s = n.path(field).asText("").trim();
        return s.isEmpty() ? null : s;
    }

    private static String nullableText(JsonNode n, String field) {
        JsonNode v = n.path(field);
        if (v.isMissingNode() || v.isNull()) {
            return null;
        }
        String s = v.asText("").trim();
        return s.isEmpty() ? null : s;
    }
}

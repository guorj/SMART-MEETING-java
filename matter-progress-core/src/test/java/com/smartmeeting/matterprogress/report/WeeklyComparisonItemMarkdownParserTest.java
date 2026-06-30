package com.smartmeeting.matterprogress.report;

import com.smartmeeting.matterprogress.model.ParsedComparisonItems;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeeklyComparisonItemMarkdownParserTest {

    private final WeeklyComparisonItemMarkdownParser parser = new WeeklyComparisonItemMarkdownParser();

    @Test
    void parse_validThreeGroups_returnsItems() {
        String md = "# 会议纪要待办事项\n\n"
                + "## 延期事项\n"
                + "- 事项：完成联调，责任人：张三，时间节点：2026-06-20，状态：延期\n"
                + "## 已完成事项\n"
                + "- 事项：写文档，责任人：李四，时间节点：2026-06-25，状态：已完成\n"
                + "## 进行中事项\n"
                + "- 无\n";
        ParsedComparisonItems result = parser.parse(md);
        assertTrue(result.success());
        assertEquals(2, result.items().size());
        assertEquals("DELAYED", result.items().get(0).category());
        assertEquals("张三", result.items().get(0).assignee());
        assertEquals("COMPLETED", result.items().get(1).category());
    }

    @Test
    void parse_missingGroupTitles_failed() {
        ParsedComparisonItems result = parser.parse("只有事项行，无 ## 标题");
        assertFalse(result.success());
        assertEquals("FAILED", result.resolveRunStatus());
    }

    @Test
    void parse_emptyGroups_failedNotReadyZero() {
        String md = "## 延期事项\n- 无\n## 已完成事项\n- 无\n## 进行中事项\n- 无\n";
        ParsedComparisonItems result = parser.parse(md);
        assertFalse(result.success());
        assertEquals("FAILED", result.resolveRunStatus());
    }

    @Test
    void parse_assigneeUnmentioned_storedAsNull() {
        String md = "## 延期事项\n"
                + "- 事项：X，责任人：未提及，时间节点：2026-06-20，状态：延期\n"
                + "## 已完成事项\n- 无\n## 进行中事项\n- 无\n";
        ParsedComparisonItems result = parser.parse(md);
        assertTrue(result.success());
        assertEquals(1, result.items().size());
        assertNull(result.items().get(0).assignee());
    }

    @Test
    void parse_statusLabelCategoryInconsistent_discarded() {
        String md = "## 延期事项\n"
                + "- 事项：X，责任人：张三，时间节点：2026-06-20，状态：已完成\n"
                + "## 已完成事项\n- 无\n## 进行中事项\n- 无\n";
        ParsedComparisonItems result = parser.parse(md);
        assertTrue(result.success());
        assertEquals(0, result.items().size());
        assertEquals(1, result.discardedCount());
        assertEquals("PARTIAL", result.resolveRunStatus());
    }
}

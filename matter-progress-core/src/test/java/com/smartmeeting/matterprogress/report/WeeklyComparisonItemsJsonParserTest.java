package com.smartmeeting.matterprogress.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.matterprogress.model.ParsedComparisonItems;
import com.smartmeeting.matterprogress.model.WeeklyComparisonItem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeeklyComparisonItemsJsonParserTest {

    private final WeeklyComparisonItemsJsonParser parser =
            new WeeklyComparisonItemsJsonParser(new ObjectMapper());

    @Test
    void parse_validBlock_returnsItems() {
        String reply = "一些说明\n-----BEGIN_WEEKLY_COMPARISON_ITEMS-----\n"
                + "{\"generatedAt\":\"2026-06-28 10:00:00\",\"items\":["
                + "{\"category\":\"DELAYED\",\"matterName\":\"完成联调\",\"assignee\":\"张三\","
                + "\"timeNode\":\"2026-06-20\",\"statusLabel\":\"延期\",\"sortOrder\":1}"
                + "]}\n-----END_WEEKLY_COMPARISON_ITEMS-----\n尾随文本";
        ParsedComparisonItems result = parser.parse(reply);
        assertTrue(result.success());
        assertEquals(1, result.items().size());
        WeeklyComparisonItem item = result.items().get(0);
        assertEquals("DELAYED", item.category());
        assertEquals("完成联调", item.matterName());
        assertEquals("张三", item.assignee());
        assertEquals(0, result.discardedCount());
        assertEquals("READY", result.resolveRunStatus());
    }

    @Test
    void parse_missingBlock_failed() {
        ParsedComparisonItems result = parser.parse("无 JSON 块的回复");
        assertFalse(result.success());
        assertEquals("FAILED", result.resolveRunStatus());
    }

    @Test
    void parse_categoryStatusLabelInconsistent_discarded() {
        String reply = "-----BEGIN_WEEKLY_COMPARISON_ITEMS-----\n"
                + "{\"items\":[{\"category\":\"DELAYED\",\"matterName\":\"X\",\"statusLabel\":\"已完成\",\"sortOrder\":1}]}"
                + "\n-----END_WEEKLY_COMPARISON_ITEMS-----";
        ParsedComparisonItems result = parser.parse(reply);
        assertTrue(result.success());
        assertEquals(0, result.items().size());
        assertEquals(1, result.discardedCount());
        assertEquals("PARTIAL", result.resolveRunStatus());
    }

    @Test
    void parse_matterNameMissing_discarded() {
        String reply = "-----BEGIN_WEEKLY_COMPARISON_ITEMS-----\n"
                + "{\"items\":[{\"category\":\"COMPLETED\",\"statusLabel\":\"已完成\",\"sortOrder\":1}]}"
                + "\n-----END_WEEKLY_COMPARISON_ITEMS-----";
        ParsedComparisonItems result = parser.parse(reply);
        assertTrue(result.success());
        assertEquals(1, result.discardedCount());
    }

    @Test
    void parse_assigneeMissing_storedAsNull() {
        String reply = "-----BEGIN_WEEKLY_COMPARISON_ITEMS-----\n"
                + "{\"items\":[{\"category\":\"IN_PROGRESS\",\"matterName\":\"Y\",\"statusLabel\":\"进行中\",\"sortOrder\":1}]}"
                + "\n-----END_WEEKLY_COMPARISON_ITEMS-----";
        ParsedComparisonItems result = parser.parse(reply);
        assertTrue(result.success());
        assertEquals(1, result.items().size());
        assertNull(result.items().get(0).assignee());
    }

    @Test
    void parse_emptyItems_readyWithZero() {
        String reply = "-----BEGIN_WEEKLY_COMPARISON_ITEMS-----\n"
                + "{\"items\":[]}\n-----END_WEEKLY_COMPARISON_ITEMS-----";
        ParsedComparisonItems result = parser.parse(reply);
        assertTrue(result.success());
        assertEquals(0, result.items().size());
        assertEquals(0, result.discardedCount());
        assertEquals("READY", result.resolveRunStatus());
    }
}

package com.smartmeeting.matterprogress.feishu;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BitableRecordSorterTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void sort_recentFirst_thenCompleted_incomplete_inProgress() {
        List<com.fasterxml.jackson.databind.JsonNode> items = new ArrayList<>();
        items.add(record("r1", "已完成", "还有10天到期", daysAgo(10), daysAgo(2), null));
        items.add(record("r2", "进行中", "还有50天到期", daysAgo(20), null, null));
        items.add(record("r3", "未完成", "还有30天到期", daysAgo(200), null, null));
        items.add(record("r4", "进行中", "还有30天到期", daysAgo(5), null, null));
        items.add(record("r5", "未完成", "还有50天到期", daysAgo(15), null, null));

        BitableRecordSorter.sort(items);

        assertThat(items.get(0).path("record_id").asText()).isEqualTo("r1");
        assertThat(items.get(1).path("record_id").asText()).isEqualTo("r5");
        assertThat(items.get(2).path("record_id").asText()).isEqualTo("r4");
        assertThat(items.get(3).path("record_id").asText()).isEqualTo("r2");
        assertThat(items.get(4).path("record_id").asText()).isEqualTo("r3");
    }

    @Test
    void completedWithinSevenDays_byStartOrDeadline() {
        assertThat(BitableRecordSorter.isCompletedWithinSevenDays(
                record("a", "已完成", "", daysAgo(1), daysAgo(3), null))).isTrue();
        assertThat(BitableRecordSorter.isCompletedWithinSevenDays(
                record("b", "已完成", "", daysAgo(1), null, daysAgo(4)))).isTrue();
        assertThat(BitableRecordSorter.isCompletedWithinSevenDays(
                record("c", "已完成", "", daysAgo(1), daysAgo(20), daysAgo(25)))).isFalse();
    }

    @Test
    void statusTier_staleCompletedGoesLast() {
        List<com.fasterxml.jackson.databind.JsonNode> items = new ArrayList<>();
        items.add(record("recent", "已完成", "", daysAgo(1), daysAgo(2), null));
        items.add(record("stale", "已完成", "", daysAgo(1), daysAgo(30), daysAgo(40)));
        items.add(record("doing", "进行中", "", daysAgo(1), null, null));
        BitableRecordSorter.sort(items);
        assertThat(items.get(0).path("record_id").asText()).isEqualTo("recent");
        assertThat(items.get(1).path("record_id").asText()).isEqualTo("doing");
        assertThat(items.get(2).path("record_id").asText()).isEqualTo("stale");
        assertThat(BitableRecordSorter.statusTier(items.get(2)))
                .isEqualTo(BitableRecordSorter.TIER_STALE_COMPLETED);
    }

    @Test
    void statusTier_threeLevels() {
        assertThat(BitableRecordSorter.statusTier(
                record("a", "已完成", "", daysAgo(1), daysAgo(2), null)))
                .isEqualTo(BitableRecordSorter.TIER_COMPLETED);
        assertThat(BitableRecordSorter.statusTier(record("b", "未完成", "", daysAgo(1), null, null)))
                .isEqualTo(BitableRecordSorter.TIER_INCOMPLETE);
        assertThat(BitableRecordSorter.statusTier(record("c", "进行中", "", daysAgo(1), null, null)))
                .isEqualTo(BitableRecordSorter.TIER_IN_PROGRESS);
    }

    @Test
    void exportMultiTable_includesTableHeaders() {
        List<com.fasterxml.jackson.databind.JsonNode> items = List.of(
                record("a", "未完成", "", daysAgo(5), daysAgo(5), null));
        var slices = List.of(
                new BitablePlainTextExporter.BitableTableSlice("tbl1", "表一", items),
                new BitablePlainTextExporter.BitableTableSlice("tbl2", "表二", List.of()));
        String text = BitablePlainTextExporter.exportMultiTable(slices, "【多维表格摘要，共 1 条 · 2 个数据表】");
        assertThat(text).contains("=== §数据表：表一 ===");
        assertThat(text).contains("=== §数据表：表二 ===");
    }

    @Test
    void export_includesSectionHeaders() {
        List<com.fasterxml.jackson.databind.JsonNode> items = new ArrayList<>();
        items.add(record("recent", "已完成", "", daysAgo(5), daysAgo(2), null));
        items.add(record("old", "未完成", "", daysAgo(200), null, null));

        String text = BitablePlainTextExporter.export(items, "【多维表格摘要，共 2 条】");

        assertThat(text).contains(BitablePlainTextExporter.SECTION_RECENT);
        assertThat(text).contains(BitablePlainTextExporter.SECTION_OLDER);
        assertThat(text.indexOf(BitablePlainTextExporter.SECTION_RECENT))
                .isLessThan(text.indexOf(BitablePlainTextExporter.SECTION_OLDER));
    }

    @Test
    void parseDaysFromText_overdue() {
        assertThat(BitableRecordSorter.parseDaysFromText("逾期 9 天")).isEqualTo(-9L);
        assertThat(BitableRecordSorter.parseDaysFromText("🕑还有221天到期")).isEqualTo(221L);
    }

    private static ObjectNode record(String id, String status, String daysText, long createdMs,
            Long startMs, Long deadlineMs) {
        ObjectNode root = JSON.createObjectNode();
        root.put("record_id", id);
        root.put("created_time", createdMs);
        ObjectNode fields = JSON.createObjectNode();
        fields.put("状态", status);
        fields.put("距离截止日", daysText);
        if (startMs != null && startMs > 0) {
            fields.put("创建日期", startMs);
        }
        if (deadlineMs != null && deadlineMs > 0) {
            fields.put("截止日期", deadlineMs);
        }
        root.set("fields", fields);
        return root;
    }

    private static long daysAgo(int days) {
        return System.currentTimeMillis() - days * 24L * 60 * 60 * 1000;
    }
}

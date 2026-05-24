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
    void sort_recentFirst_thenIncomplete_thenDaysDesc() {
        List<com.fasterxml.jackson.databind.JsonNode> items = new ArrayList<>();
        items.add(record("r1", "已完成", "还有10天到期", daysAgo(10)));
        items.add(record("r2", "进行中", "还有50天到期", daysAgo(20)));
        items.add(record("r3", "进行中", "还有30天到期", daysAgo(200)));
        items.add(record("r4", "进行中", "还有30天到期", daysAgo(5)));

        BitableRecordSorter.sort(items);

        assertThat(items.get(0).path("record_id").asText()).isEqualTo("r2");
        assertThat(items.get(1).path("record_id").asText()).isEqualTo("r4");
        assertThat(items.get(2).path("record_id").asText()).isEqualTo("r1");
        assertThat(items.get(3).path("record_id").asText()).isEqualTo("r3");
    }

    @Test
    void parseDaysFromText_overdue() {
        assertThat(BitableRecordSorter.parseDaysFromText("逾期 9 天")).isEqualTo(-9L);
        assertThat(BitableRecordSorter.parseDaysFromText("🕑还有221天到期")).isEqualTo(221L);
    }

    private static ObjectNode record(String id, String status, String daysText, long createdMs) {
        ObjectNode root = JSON.createObjectNode();
        root.put("record_id", id);
        root.put("created_time", createdMs);
        ObjectNode fields = JSON.createObjectNode();
        fields.put("状态", status);
        fields.put("距离截止日", daysText);
        root.set("fields", fields);
        return root;
    }

    private static long daysAgo(int days) {
        return System.currentTimeMillis() - days * 24L * 60 * 60 * 1000;
    }
}

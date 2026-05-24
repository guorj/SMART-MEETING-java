package com.smartmeeting.matterprogress.feishu;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BitableFieldFormatterTest {

    @Test
    void formatRawString_booleanAndDate() {
        assertThat(BitableFieldFormatter.formatRawString("true", "是否已完成")).isEqualTo("是");
        assertThat(BitableFieldFormatter.formatRawString("false", "是否已完成")).isEqualTo("否");
        assertThat(BitableFieldFormatter.formatRawString("1757260800000", "创建时间"))
                .matches("\\d{4}-\\d{2}-\\d{2}");
        assertThat(BitableFieldFormatter.formatRawString("1759939200000", "截止日期"))
                .isEqualTo("2025-10-09");
    }

    @Test
    void formatRawString_typeValueJson() {
        String json = "{\"type\":1,\"value\":[{\"text\":\"🕑还有221天到期\",\"type\":\"text\"}]}";
        assertThat(BitableFieldFormatter.formatRawString(json, "距离截止日"))
                .isEqualTo("🕑还有221天到期");
    }
}

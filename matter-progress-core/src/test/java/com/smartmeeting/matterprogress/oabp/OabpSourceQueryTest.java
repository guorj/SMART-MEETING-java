package com.smartmeeting.matterprogress.oabp;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OabpSourceQueryTest {

    @Test
    void toMarkdownTable_formatsHeadersAndRows() {
        OabpSourceQuery.QueryResult result = new OabpSourceQuery.QueryResult(
                List.of("task_name", "status"),
                List.of(
                        List.of("任务A", "进行中"),
                        List.of("任务B", "已完成")));
        String md = OabpSourceQuery.toMarkdownTable(result);
        assertThat(md).contains("| task_name | status |");
        assertThat(md).contains("| 任务A | 进行中 |");
        assertThat(md).contains("| 任务B | 已完成 |");
    }

    @Test
    void toMarkdownTable_emptyRows() {
        OabpSourceQuery.QueryResult result = new OabpSourceQuery.QueryResult(
                List.of("col"),
                List.of());
        String md = OabpSourceQuery.toMarkdownTable(result);
        assertThat(md).contains("_无数据_");
    }

    @Test
    void formatCell_handlesNullAndBoolean() {
        assertThat(OabpSourceQuery.formatCell(null)).isEmpty();
        assertThat(OabpSourceQuery.formatCell(true)).isEqualTo("1");
        assertThat(OabpSourceQuery.formatCell(false)).isEqualTo("0");
    }
}

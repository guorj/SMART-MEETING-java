package com.smartmeeting.matterprogress.feishu;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FeishuSpreadsheetPlainTextFetcherTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void columnIndexToLetter() {
        assertThat(FeishuSpreadsheetPlainTextFetcher.columnIndexToLetter(0)).isEqualTo("A");
        assertThat(FeishuSpreadsheetPlainTextFetcher.columnIndexToLetter(25)).isEqualTo("Z");
        assertThat(FeishuSpreadsheetPlainTextFetcher.columnIndexToLetter(26)).isEqualTo("AA");
    }

    @Test
    void buildReadRangeUsesGridProperties() throws Exception {
        var grid = JSON.readTree("{\"row_count\":120,\"column_count\":8}");
        assertThat(FeishuSpreadsheetPlainTextFetcher.buildReadRange("abc123", grid))
                .isEqualTo("abc123!A1:H120");
    }

    @Test
    void isReadableSheetResource_onlyGridSheet() throws Exception {
        assertThat(FeishuSpreadsheetPlainTextFetcher.isReadableSheetResource(
                JSON.readTree("{\"resource_type\":\"sheet\",\"sheet_id\":\"a\"}"))).isTrue();
        assertThat(FeishuSpreadsheetPlainTextFetcher.isReadableSheetResource(
                JSON.readTree("{\"resource_type\":\"bitable\",\"sheet_id\":\"a\"}"))).isFalse();
        assertThat(FeishuSpreadsheetPlainTextFetcher.isReadableSheetResource(
                JSON.readTree("{\"sheet_id\":\"a\"}"))).isTrue();
    }

    @Test
    void encodeRangeForUrlPath_keepsExclamation() {
        assertThat(FeishuSpreadsheetPlainTextFetcher.encodeRangeForUrlPath("Q7PlXT!A1:Z200"))
                .isEqualTo("Q7PlXT!A1:Z200");
    }

    @Test
    void isSheetIdNotFound_detects90215() {
        assertThat(FeishuSpreadsheetPlainTextFetcher.isSheetIdNotFound(
                new RuntimeException("飞书 sheets values code=90215 msg=not found sheetId"))).isTrue();
    }

    @Test
    void formatValuesAsMarkdownTable() throws Exception {
        var values = JSON.readTree("""
                [
                  ["姓名", "部门"],
                  ["张三", "综合"]
                ]
                """);
        String md = FeishuSpreadsheetPlainTextFetcher.formatValuesAsMarkdownTable("汇报表", values);
        assertThat(md).contains("## 工作表：汇报表");
        assertThat(md).contains("| 姓名 | 部门 |");
        assertThat(md).contains("| 张三 | 综合 |");
    }
}

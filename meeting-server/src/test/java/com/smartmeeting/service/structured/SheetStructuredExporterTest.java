package com.smartmeeting.service.structured;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.api.dto.structured.SheetStructuredDto;
import com.smartmeeting.matterprogress.feishu.FeishuSpreadsheetPlainTextFetcher;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SheetStructuredExporterTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void exportsHeadersAndRows() throws Exception {
        var values = mapper.readTree("""
                [["姓名", "进度"], ["张三", "80%"], ["李四", "100%"]]
                """);
        SheetStructuredDto dto = SheetStructuredExporter.export("Sheet1", values);
        assertNotNull(dto);
        assertEquals("Sheet1", dto.getSheetName());
        assertEquals(2, dto.getHeaders().size());
        assertEquals("姓名", dto.getHeaders().get(0));
        assertEquals(2, dto.getRows().size());
        assertEquals("张三", dto.getRows().get(0).get(0));
    }

    @Test
    void trimsTrailingEmptyColumns() throws Exception {
        var values = mapper.readTree("""
                [["序号", "项目名称", "", "", ""], ["01", "测试项目", "", "", ""]]
                """);
        SheetStructuredDto dto = SheetStructuredExporter.export("项目总览", values);
        assertEquals(2, dto.getHeaders().size());
        assertEquals("序号", dto.getHeaders().get(0));
        assertEquals(2, dto.getRows().get(0).size());
    }

    @Test
    void trimsTrailingAndMiddleEmptyRows() throws Exception {
        var values = mapper.readTree("""
                [["项目", "负责人"], ["A", "张三"], ["", ""], ["B", "李四"], ["", ""], ["", ""]]
                """);
        SheetStructuredDto dto = SheetStructuredExporter.export("项目总览", values);
        assertEquals(2, dto.getRows().size());
        assertEquals("A", dto.getRows().get(0).get(0));
        assertEquals("B", dto.getRows().get(1).get(0));
    }

    @Test
    void exportsMergedRanges() throws Exception {
        var values = mapper.readTree("""
                [["A", "B"], ["1", "2"], ["3", "4"]]
                """);
        var merges = List.of(new FeishuSpreadsheetPlainTextFetcher.MergeRange(0, 1, 0, 1));
        SheetStructuredDto dto = SheetStructuredExporter.export("Sheet1", values, merges);
        assertEquals(1, dto.getMergedRanges().size());
        assertEquals(0, dto.getMergedRanges().get(0).getStartRow());
        assertEquals(1, dto.getMergedRanges().get(0).getEndCol());
    }
}

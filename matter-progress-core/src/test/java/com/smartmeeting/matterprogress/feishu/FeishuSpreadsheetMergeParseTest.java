package com.smartmeeting.matterprogress.feishu;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeishuSpreadsheetMergeParseTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parsesMergesRelativeToHeaderRow() throws Exception {
        var sheet = mapper.readTree("""
                {
                  "merges": [
                    {"start_row_index": 0, "end_row_index": 0, "start_column_index": 0, "end_column_index": 1},
                    {"start_row_index": 2, "end_row_index": 3, "start_column_index": 1, "end_column_index": 2}
                  ]
                }
                """);
        List<FeishuSpreadsheetPlainTextFetcher.MergeRange> ranges =
                FeishuSpreadsheetPlainTextFetcher.parseSheetMerges(sheet, 1);
        assertEquals(1, ranges.size());
        assertEquals(1, ranges.get(0).startRow());
        assertEquals(2, ranges.get(0).endRow());
        assertEquals(1, ranges.get(0).startCol());
        assertEquals(2, ranges.get(0).endCol());
    }

    @Test
    void returnsEmptyWhenNoMerges() throws Exception {
        var sheet = mapper.readTree("{\"merges\":[]}");
        assertTrue(FeishuSpreadsheetPlainTextFetcher.parseSheetMerges(sheet, 1).isEmpty());
    }
}

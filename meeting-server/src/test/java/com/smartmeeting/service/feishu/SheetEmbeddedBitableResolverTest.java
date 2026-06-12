package com.smartmeeting.service.feishu;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SheetEmbeddedBitableResolverTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void findsEmbeddedBitableFromMetainfoByTableId() throws Exception {
        var metainfo = mapper.readTree("""
                {
                  "sheets": [{
                    "sheetId": "4d4df8",
                    "title": "任务跟进看板",
                    "blockInfo": {
                      "blockType": "BITABLE_BLOCK",
                      "blockToken": "DhpDbvLGkaiWxhsOnWDcanQ2n8b_tblOHTtHGNpyzKAY"
                    }
                  }]
                }
                """);
        var found = SheetEmbeddedBitableResolver.findEmbeddedBitable(metainfo, "tblOHTtHGNpyzKAY");
        assertNotNull(found);
        assertEquals("DhpDbvLGkaiWxhsOnWDcanQ2n8b", found.appToken());
        assertEquals("tblOHTtHGNpyzKAY", found.tableId());
    }

    @Test
    void returnsSingleEmbedWhenTableIdBlank() throws Exception {
        var metainfo = mapper.readTree("""
                {
                  "sheets": [{
                    "blockInfo": {
                      "blockType": "BITABLE_BLOCK",
                      "blockToken": "appTok_tblOnlyTable"
                    }
                  }]
                }
                """);
        var found = SheetEmbeddedBitableResolver.findEmbeddedBitable(metainfo, null);
        assertNotNull(found);
        assertEquals("tblOnlyTable", found.tableId());
    }

    @Test
    void ignoresGridSheetsWithoutBitableBlock() throws Exception {
        var metainfo = mapper.readTree("""
                {
                  "sheets": [{
                    "sheetId": "grid1",
                    "title": "普通表",
                    "rowCount": 10
                  }]
                }
                """);
        assertNull(SheetEmbeddedBitableResolver.findEmbeddedBitable(metainfo, "tblX"));
    }
}

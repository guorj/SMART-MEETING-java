package com.smartmeeting.service.feishu;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class DocxEmbeddedBitableResolverTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parsesCombinedToken() {
        var ref = DocxEmbeddedBitableResolver.parseCombinedToken(
                "MMLLb4qYna4FrgsX5THc6EOTnT2_tblacTqz7wOURGpd");
        assertNotNull(ref);
        assertEquals("MMLLb4qYna4FrgsX5THc6EOTnT2", ref.appToken());
        assertEquals("tblacTqz7wOURGpd", ref.tableId());
    }

    @Test
    void findsEmbeddedBitableByTableIdWithPrefixTypo() throws Exception {
        var items = mapper.readTree("""
                [{"block_type":18,"block_id":"b1","bitable":{"token":"appTok_tblOHTtHGNpyzKAY"}}]
                """);
        var found = DocxEmbeddedBitableResolver.findEmbeddedBitable(items, "tblOHTtHGNpyzKAY1");
        assertNotNull(found);
        assertEquals("appTok", found.appToken());
        assertEquals("tblOHTtHGNpyzKAY", found.tableId());
    }

    @Test
    void returnsSingleEmbedWhenTableIdBlank() throws Exception {
        var items = mapper.readTree("""
                [{"block_type":18,"bitable":{"token":"onlyApp_tblOnlyTable"}}]
                """);
        var found = DocxEmbeddedBitableResolver.findEmbeddedBitable(items, null);
        assertNotNull(found);
        assertEquals("tblOnlyTable", found.tableId());
    }

    @Test
    void returnsNullWhenMultipleEmbedsAndNoTableId() throws Exception {
        var items = mapper.readTree("""
                [
                  {"block_type":18,"bitable":{"token":"a_tbl1"}},
                  {"block_type":18,"bitable":{"token":"a_tbl2"}}
                ]
                """);
        assertNull(DocxEmbeddedBitableResolver.findEmbeddedBitable(items, ""));
    }
}

package com.smartmeeting.service.feishu;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BitableTableIdResolverTest {

    @Test
    void resolvesExactMatch() {
        assertEquals("tblABC", BitableTableIdResolver.resolveFromListing("tblABC", List.of("tblABC", "tblXYZ")));
    }

    @Test
    void resolvesCaseInsensitiveUniqueMatch() {
        assertEquals("tblAbc", BitableTableIdResolver.resolveFromListing("tblabc", List.of("tblAbc")));
    }

    @Test
    void resolvesPrefixMatchForTrailingTypo() {
        assertEquals(
                "tblOHTtHGNpyzKAY",
                BitableTableIdResolver.resolveFromListing(
                        "tblOHTtHGNpyzKAY1",
                        List.of("tblOHTtHGNpyzKAY", "tblOther")));
    }

    @Test
    void returnsNullWhenPrefixMatchAmbiguous() {
        assertNull(BitableTableIdResolver.resolveFromListing(
                "tblOHT",
                List.of("tblOHTtHGNpyzKAY", "tblOHTOther")));
    }

    @Test
    void detectsWrongTableIdError() {
        assertTrue(BitableTableIdResolver.isWrongTableIdError(
                new RuntimeException("飞书 bitable records/search code=1254004 WrongTableId")));
        assertFalse(BitableTableIdResolver.isWrongTableIdError(new RuntimeException("other")));
    }
}

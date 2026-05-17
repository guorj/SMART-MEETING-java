package com.smartmeeting.service;

import com.smartmeeting.entity.MatterProgressDocConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatterProgressDocxIdResolverTest {

    @Test
    void parsesDocxFromUrl() {
        MatterProgressDocConfig c = new MatterProgressDocConfig();
        c.setFeishuDocUrl("https://x.feishu.cn/docx/doxcnZZZ?from=from_copylink");
        assertEquals("doxcnZZZ", MatterProgressDocxIdResolver.resolveDocumentId(c));
    }

    @Test
    void wikiUrlResolvesNodeToken() {
        MatterProgressDocConfig c = new MatterProgressDocConfig();
        c.setFeishuDocUrl("https://sample.feishu.cn/wiki/wikiTnXxXx");
        assertEquals("wikiTnXxXx", MatterProgressDocxIdResolver.resolveDocumentId(c));
        assertTrue(MatterProgressDocxIdResolver.hasFeishuFields(c));
    }

    @Test
    void baseUrlResolvesAppToken() {
        MatterProgressDocConfig c = new MatterProgressDocConfig();
        c.setFeishuDocUrl("https://sample.feishu.cn/base/bascnApp01?table=tblXXX&view=vewYYY");
        assertEquals("bascnApp01", MatterProgressDocxIdResolver.resolveDocumentId(c));
    }

    @Test
    void noFields() {
        MatterProgressDocConfig c = new MatterProgressDocConfig();
        assertNull(MatterProgressDocxIdResolver.resolveDocumentId(c));
        assertFalse(MatterProgressDocxIdResolver.hasFeishuFields(c));
    }
}

package com.smartmeeting.service;

import com.smartmeeting.entity.MatterProgressDocConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatterProgressDocxIdResolverTest {

    @Test
    void prefersTokenOverUrl() {
        MatterProgressDocConfig c = new MatterProgressDocConfig();
        c.setFeishuDocToken("doxcAAA");
        c.setFeishuDocUrl("https://x.feishu.cn/docx/doxcBBB");
        assertEquals("doxcAAA", MatterProgressDocxIdResolver.resolveDocumentId(c));
    }

    @Test
    void parsesDocxFromUrl() {
        MatterProgressDocConfig c = new MatterProgressDocConfig();
        c.setFeishuDocUrl("https://sample.feishu.cn/docx/doxcnZZZ?from=from_copylink");
        assertEquals("doxcnZZZ", MatterProgressDocxIdResolver.resolveDocumentId(c));
    }

    @Test
    void wikiUrlNotParsedAsDocx() {
        MatterProgressDocConfig c = new MatterProgressDocConfig();
        c.setFeishuDocUrl("https://sample.feishu.cn/wiki/wikiTnXxXx");
        assertNull(MatterProgressDocxIdResolver.resolveDocumentId(c));
        assertTrue(MatterProgressDocxIdResolver.hasFeishuFields(c));
    }

    @Test
    void blankTokenFallsBackToUrl() {
        MatterProgressDocConfig c = new MatterProgressDocConfig();
        c.setFeishuDocToken("   ");
        c.setFeishuDocUrl("https://a.cn/docx/doxcOK");
        assertEquals("doxcOK", MatterProgressDocxIdResolver.resolveDocumentId(c));
    }

    @Test
    void noFields() {
        MatterProgressDocConfig c = new MatterProgressDocConfig();
        assertNull(MatterProgressDocxIdResolver.resolveDocumentId(c));
        assertFalse(MatterProgressDocxIdResolver.hasFeishuFields(c));
    }
}

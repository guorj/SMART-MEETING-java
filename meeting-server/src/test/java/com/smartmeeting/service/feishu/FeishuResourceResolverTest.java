package com.smartmeeting.service.feishu;

import com.smartmeeting.entity.MatterProgressDocConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeishuResourceResolverTest {

    @Test
    void parsesDocxUrl() {
        FeishuResourceRef ref = FeishuResourceResolver.resolve("https://a.feishu.cn/docx/doxcnAAA?from=copy");
        assertNotNull(ref);
        assertEquals(FeishuResourceKind.DOCX, ref.kind());
        assertEquals("doxcnAAA", ref.primaryToken());
    }

    @Test
    void parsesWikiUrl() {
        FeishuResourceRef ref = FeishuResourceResolver.resolve("https://a.feishu.cn/wiki/wikiNode01");
        assertNotNull(ref);
        assertEquals(FeishuResourceKind.WIKI, ref.kind());
        assertEquals("wikiNode01", ref.primaryToken());
        assertTrue(ref.canFetchPlainText());
    }

    @Test
    void parsesWikiUrlWithEmbeddedBitableTable() {
        FeishuResourceRef ref = FeishuResourceResolver.resolve(
                "https://ovjde0k7vc1.feishu.cn/wiki/AWufwT0tWiLCRKkdmhIcek5JnIg?table=tblxP8EAtOOQq7mt&view=vewM1Y9Vem");
        assertNotNull(ref);
        assertEquals(FeishuResourceKind.WIKI, ref.kind());
        assertEquals("AWufwT0tWiLCRKkdmhIcek5JnIg", ref.primaryToken());
        assertEquals("tblxP8EAtOOQq7mt", ref.tableId());
        assertTrue(ref.canFetchPlainText());
    }

    @Test
    void parsesBaseUrlWithTable() {
        FeishuResourceRef ref = FeishuResourceResolver.resolve(
                "https://a.feishu.cn/base/bascnApp?table=tbl01&view=vew02");
        assertNotNull(ref);
        assertEquals(FeishuResourceKind.BASE, ref.kind());
        assertEquals("bascnApp", ref.primaryToken());
        assertEquals("tbl01", ref.tableId());
        assertEquals("vew02", ref.viewId());
        assertTrue(ref.canFetchPlainText());
    }

    @Test
    void baseWithoutTableCannotFetchPlainText() {
        FeishuResourceRef ref = FeishuResourceResolver.resolve("https://a.feishu.cn/base/bascnApp");
        assertNotNull(ref);
        assertEquals(FeishuResourceKind.BASE, ref.kind());
        assertTrue(ref.showOnHostPage());
        assertTrue(!ref.canFetchPlainText());
    }

    @Test
    void legacyDocIdToDocxUrl() {
        FeishuResourceRef ref = FeishuResourceResolver.resolve(
                FeishuResourceResolver.legacyDocIdToDocxUrl("doxcOnly"));
        assertNotNull(ref);
        assertEquals(FeishuResourceKind.DOCX, ref.kind());
        assertEquals("doxcOnly", ref.primaryToken());
    }

    @Test
    void legacyDocumentIdFromConfig() {
        MatterProgressDocConfig c = new MatterProgressDocConfig();
        c.setFeishuDocUrl("https://a.feishu.cn/base/appTok?table=tblX");
        assertEquals("appTok", FeishuResourceResolver.resolveLegacyDocumentId(c));
    }

    @Test
    void emptyReturnsNull() {
        assertNull(FeishuResourceResolver.resolve((String) null));
        assertNull(FeishuResourceResolver.resolve("  "));
    }

    @Test
    void parsesTenantSampleUrls() {
        FeishuResourceRef docx = FeishuResourceResolver.resolve(
                "https://ovjde0k7vc1.feishu.cn/docx/CzrSd90yMoKnsoxR4xGcEI5Fncf");
        assertEquals(FeishuResourceKind.DOCX, docx.kind());
        assertEquals("CzrSd90yMoKnsoxR4xGcEI5Fncf", docx.primaryToken());

        FeishuResourceRef wiki = FeishuResourceResolver.resolve(
                "https://ovjde0k7vc1.feishu.cn/wiki/BO4Kwdv65izpo8knLdWcr2UZns2");
        assertEquals(FeishuResourceKind.WIKI, wiki.kind());

        FeishuResourceRef base = FeishuResourceResolver.resolve(
                "https://ovjde0k7vc1.feishu.cn/base/SnsXbyQ1Qa57fCsI8mrcRAIbnve?table=tbl7viO4AJ4ebD0B&view=vew3qfhSyY");
        assertEquals(FeishuResourceKind.BASE, base.kind());
        assertEquals("tbl7viO4AJ4ebD0B", base.tableId());
    }

    @Test
    void rejectsNonFeishuAppUrls() {
        assertNull(FeishuResourceResolver.resolve(
                "https://39.97.61.212/rec/896023ae-0979-462b-b792-fd8169fb7b0c?token=abc"));
        assertTrue(!FeishuResourceResolver.isRecognizedFeishuDocUrl(
                "https://39.97.61.212/host/meeting-id"));
    }

    @Test
    void isRecognizedFeishuDocUrl_matchesParsedTypes() {
        assertTrue(FeishuResourceResolver.isRecognizedFeishuDocUrl(
                "https://ovjde0k7vc1.feishu.cn/base/SnsXbyQ1Qa57fCsI8mrcRAIbnve?table=tbl7viO4AJ4ebD0B&view=vew3qfhSyY"));
    }
}

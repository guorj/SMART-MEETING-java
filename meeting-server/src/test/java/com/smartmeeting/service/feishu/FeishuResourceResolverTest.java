package com.smartmeeting.service.feishu;

import com.smartmeeting.entity.MatterProgressDocConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FeishuResourceResolver} 单元测试：验证飞书 docx/wiki/base URL 解析与识别逻辑。
 */
class FeishuResourceResolverTest {

    /** 解析 docx URL 应返回 DOCX 类型及 primaryToken。 */
    @Test
    void parsesDocxUrl() {
        FeishuResourceRef ref = FeishuResourceResolver.resolve("https://a.feishu.cn/docx/doxcnAAA?from=copy");
        assertNotNull(ref);
        assertEquals(FeishuResourceKind.DOCX, ref.kind());
        assertEquals("doxcnAAA", ref.primaryToken());
    }

    /** 解析 wiki URL 应返回 WIKI 类型且可拉取纯文本。 */
    @Test
    void parsesWikiUrl() {
        FeishuResourceRef ref = FeishuResourceResolver.resolve("https://a.feishu.cn/wiki/wikiNode01");
        assertNotNull(ref);
        assertEquals(FeishuResourceKind.WIKI, ref.kind());
        assertEquals("wikiNode01", ref.primaryToken());
        assertTrue(ref.canFetchPlainText());
    }

    /** 解析含内嵌多维表格的 wiki URL 应提取 tableId。 */
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

    /** 解析 base URL 应提取 appToken、tableId 与 viewId。 */
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

    /** 无 table 参数的 base URL 不可拉取纯文本但可在主持页展示。 */
    @Test
    void baseWithoutTableCannotFetchPlainText() {
        FeishuResourceRef ref = FeishuResourceResolver.resolve("https://a.feishu.cn/base/bascnApp");
        assertNotNull(ref);
        assertEquals(FeishuResourceKind.BASE, ref.kind());
        assertTrue(ref.showOnHostPage());
        assertTrue(!ref.canFetchPlainText());
    }

    /** legacy docId 应转换为 docx URL 并正确解析。 */
    @Test
    void legacyDocIdToDocxUrl() {
        FeishuResourceRef ref = FeishuResourceResolver.resolve(
                FeishuResourceResolver.legacyDocIdToDocxUrl("doxcOnly"));
        assertNotNull(ref);
        assertEquals(FeishuResourceKind.DOCX, ref.kind());
        assertEquals("doxcOnly", ref.primaryToken());
    }

    /** 从配置对象解析 legacy documentId。 */
    @Test
    void legacyDocumentIdFromConfig() {
        MatterProgressDocConfig c = new MatterProgressDocConfig();
        c.setFeishuDocUrl("https://a.feishu.cn/base/appTok?table=tblX");
        assertEquals("appTok", FeishuResourceResolver.resolveLegacyDocumentId(c));
    }

    /** null 或空白 URL 应返回 null。 */
    @Test
    void emptyReturnsNull() {
        assertNull(FeishuResourceResolver.resolve((String) null));
        assertNull(FeishuResourceResolver.resolve("  "));
    }

    /** 租户样例 URL 应正确解析 docx、wiki 与 base 类型。 */
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

    /** 非飞书应用 URL 应被拒绝识别。 */
    @Test
    void rejectsNonFeishuAppUrls() {
        assertNull(FeishuResourceResolver.resolve(
                "https://39.97.61.212/rec/896023ae-0979-462b-b792-fd8169fb7b0c?token=abc"));
        assertTrue(!FeishuResourceResolver.isRecognizedFeishuDocUrl(
                "https://39.97.61.212/host/meeting-id"));
    }

    /** isRecognizedFeishuDocUrl 应与解析结果类型一致。 */
    @Test
    void isRecognizedFeishuDocUrl_matchesParsedTypes() {
        assertTrue(FeishuResourceResolver.isRecognizedFeishuDocUrl(
                "https://ovjde0k7vc1.feishu.cn/base/SnsXbyQ1Qa57fCsI8mrcRAIbnve?table=tbl7viO4AJ4ebD0B&view=vew3qfhSyY"));
    }
}

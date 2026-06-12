package com.smartmeeting.service.feishu;

import com.smartmeeting.config.agenda.AgendaDocBindingSnapshot;
import com.smartmeeting.config.feishu.FeishuResourceKind;
import com.smartmeeting.config.feishu.FeishuResourceRef;
import com.smartmeeting.config.feishu.FeishuResourceResolver;
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

    /** 同资源不同域名 URL 的 dedupeKey 应一致。 */
    @Test
    void dedupeKeyIgnoresHostForSameBaseTable() {
        FeishuResourceRef a = FeishuResourceResolver.resolve(
                "https://ovjde0k7vc1.feishu.cn/base/GYoHbrmQYaPtflsUubDcJGyknGd?table=tblcukp9eKr3REI7&view=vewM1Y9Vem");
        FeishuResourceRef b = FeishuResourceResolver.resolve(
                "https://bytedance.feishu.cn/base/GYoHbrmQYaPtflsUubDcJGyknGd?table=tblcukp9eKr3REI7&view=vewM1Y9Vem");
        assertNotNull(a);
        assertNotNull(b);
        assertEquals(a.dedupeKey(), b.dedupeKey());
    }

    /** query 中 table 参数支持 URL 编码。 */
    @Test
    void parsesBaseUrlWithEncodedTableParam() {
        FeishuResourceRef ref = FeishuResourceResolver.resolve(
                "https://a.feishu.cn/base/bascnApp?table=tbl%2FXY&view=vew02");
        assertNotNull(ref);
        assertEquals("tbl/XY", ref.tableId());
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

    /** 无 table 参数的 base URL 可拉取全库 plainText/structured。 */
    @Test
    void baseWithoutTableCanFetchPlainText() {
        FeishuResourceRef ref = FeishuResourceResolver.resolve("https://a.feishu.cn/base/bascnApp");
        assertNotNull(ref);
        assertEquals(FeishuResourceKind.BASE, ref.kind());
        assertTrue(ref.showOnHostPage());
        assertTrue(ref.canFetchPlainText());
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
        AgendaDocBindingSnapshot c = AgendaDocBindingSnapshot.builder()
                .feishuDocUrl("https://a.feishu.cn/base/appTok?table=tblX")
                .build();
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

    /** base 无 table= 时应解析为全库拉取（tableId 为空）。 */
    @Test
    void parsesBaseUrlWithoutTableParam() {
        FeishuResourceRef ref = FeishuResourceResolver.resolve(
                "https://ovjde0k7vc1.feishu.cn/base/SnsXbyQ1Qa57fCsI8mrcRAIbnve");
        assertNotNull(ref);
        assertEquals(FeishuResourceKind.BASE, ref.kind());
        assertEquals("SnsXbyQ1Qa57fCsI8mrcRAIbnve", ref.primaryToken());
        assertNull(ref.tableId());
        assertTrue(ref.canFetchPlainText());
    }

    /** 非飞书应用 URL 应被拒绝识别。 */
    @Test
    void rejectsNonFeishuAppUrls() {
        assertNull(FeishuResourceResolver.resolve(
                "https://39.97.61.212/rec/896023ae-0979-462b-b792-fd8169fb7b0c?token=abc"));
        assertTrue(!FeishuResourceResolver.isRecognizedFeishuDocUrl(
                "https://39.97.61.212/host/meeting-id"));
    }

    /** 解析 /sheets/ 直链应返回 SHEET 类型。 */
    @Test
    void parsesSheetsUrl() {
        FeishuResourceRef ref = FeishuResourceResolver.resolve("https://a.feishu.cn/sheets/shtcnSheet01");
        assertNotNull(ref);
        assertEquals(FeishuResourceKind.SHEET, ref.kind());
        assertEquals("shtcnSheet01", ref.primaryToken());
        assertTrue(ref.canFetchPlainText());
    }

    /** isRecognizedFeishuDocUrl 应与解析结果类型一致。 */
    @Test
    void isRecognizedFeishuDocUrl_matchesParsedTypes() {
        assertTrue(FeishuResourceResolver.isRecognizedFeishuDocUrl(
                "https://ovjde0k7vc1.feishu.cn/base/SnsXbyQ1Qa57fCsI8mrcRAIbnve?table=tbl7viO4AJ4ebD0B&view=vew3qfhSyY"));
    }

    /** 会序问题链接：wiki + table 参数应完整解析 node_token 与 tableId。 */
    @Test
    void parsesUserReportedWikiBitableUrl() {
        FeishuResourceRef ref = FeishuResourceResolver.resolve(
                "https://ovjde0k7vc1.feishu.cn/wiki/HK6vwomEni9TpRkgQXncmbMQngf?table=tblOHTtHGNpyzKAY1");
        assertNotNull(ref);
        assertEquals(FeishuResourceKind.WIKI, ref.kind());
        assertEquals("HK6vwomEni9TpRkgQXncmbMQngf", ref.primaryToken());
        assertEquals("tblOHTtHGNpyzKAY1", ref.tableId());
        assertTrue(ref.canFetchPlainText());
    }

    @Test
    void parsesUserReportedWikiBitableUrlWithoutTrailingDigit() {
        FeishuResourceRef ref = FeishuResourceResolver.resolve(
                "https://ovjde0k7vc1.feishu.cn/wiki/HK6vwomEni9TpRkgQXncmbMQngf?table=tblOHTtHGNpyzKAY");
        assertNotNull(ref);
        assertEquals("tblOHTtHGNpyzKAY", ref.tableId());
    }

    /** 任务清单 AppLink 应解析为 TASKLIST 且 primaryToken 为 guid。 */
    @Test
    void parsesApplinkTaskListUrl() {
        String url = "https://applink.feishu.cn/client/todo/task_list?guid=3debd4f2-1f74-4e8c-89a1-e43be74b0dc4";
        FeishuResourceRef ref = FeishuResourceResolver.resolve(url);
        assertNotNull(ref);
        assertEquals(FeishuResourceKind.TASKLIST, ref.kind());
        assertEquals("3debd4f2-1f74-4e8c-89a1-e43be74b0dc4", ref.primaryToken());
        assertTrue(ref.canFetchPlainText());
        assertTrue(ref.showOnHostPage());
        assertTrue(FeishuResourceResolver.isRecognizedFeishuDocUrl(url));
        assertEquals(url, ref.defaultOpenUrl());
    }

    /** 无 guid 的 task_list AppLink 无法解析。 */
    @Test
    void rejectsApplinkTaskListWithoutGuid() {
        assertNull(FeishuResourceResolver.resolve(
                "https://applink.feishu.cn/client/todo/task_list"));
    }

    /** 无 sourceUrl 时 wiki defaultOpenUrl 应保留 table 锚点。 */
    @Test
    void wikiDefaultOpenUrlPreservesTableParam() {
        FeishuResourceRef ref = new FeishuResourceRef(
                FeishuResourceKind.WIKI, "wikiNode01", "tbl01", "vew02", null, null);
        assertEquals("https://bytedance.feishu.cn/wiki/wikiNode01?table=tbl01&view=vew02", ref.defaultOpenUrl());
    }
}

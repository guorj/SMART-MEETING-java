package com.smartmeeting.service;

import com.smartmeeting.entity.MatterProgressDocConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MatterProgressDocxIdResolver} 单元测试：验证从飞书 URL 解析文档 ID 的逻辑。
 */
class MatterProgressDocxIdResolverTest {

    /** 从 docx URL 中解析 documentId。 */
    @Test
    void parsesDocxFromUrl() {
        MatterProgressDocConfig c = new MatterProgressDocConfig();
        c.setFeishuDocUrl("https://x.feishu.cn/docx/doxcnZZZ?from=from_copylink");
        assertEquals("doxcnZZZ", MatterProgressDocxIdResolver.resolveDocumentId(c));
    }

    /** 从 wiki URL 中解析 nodeToken。 */
    @Test
    void wikiUrlResolvesNodeToken() {
        MatterProgressDocConfig c = new MatterProgressDocConfig();
        c.setFeishuDocUrl("https://sample.feishu.cn/wiki/wikiTnXxXx");
        assertEquals("wikiTnXxXx", MatterProgressDocxIdResolver.resolveDocumentId(c));
        assertTrue(MatterProgressDocxIdResolver.hasFeishuFields(c));
    }

    /** 从多维表格 base URL 中解析 appToken。 */
    @Test
    void baseUrlResolvesAppToken() {
        MatterProgressDocConfig c = new MatterProgressDocConfig();
        c.setFeishuDocUrl("https://sample.feishu.cn/base/bascnApp01?table=tblXXX&view=vewYYY");
        assertEquals("bascnApp01", MatterProgressDocxIdResolver.resolveDocumentId(c));
    }

    /** 配置无飞书字段时应返回 null 且 hasFeishuFields 为 false。 */
    @Test
    void noFields() {
        MatterProgressDocConfig c = new MatterProgressDocConfig();
        assertNull(MatterProgressDocxIdResolver.resolveDocumentId(c));
        assertFalse(MatterProgressDocxIdResolver.hasFeishuFields(c));
    }
}

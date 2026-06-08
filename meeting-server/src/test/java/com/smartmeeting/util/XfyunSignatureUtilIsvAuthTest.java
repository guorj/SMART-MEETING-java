package com.smartmeeting.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XfyunSignatureUtilIsvAuthTest {

    @Test
    void buildIsvPostAuthUrl_encodesDateWithoutPlus() throws Exception {
        XfyunSignatureUtil.IsvAuthContext auth = XfyunSignatureUtil.buildIsvPostAuthUrl(
                "https://api.xf-yun.com/v1/private/s1aa729d0",
                "test-api-key",
                "test-api-secret");
        String query = auth.signedUri().getRawQuery();
        assertTrue(query.contains("authorization="));
        assertTrue(query.contains("host=api.xf-yun.com"));
        assertTrue(query.contains("date="));
        assertFalse(query.contains("+"), "date 查询参数不应含 +，避免被解析为空格");
        assertTrue(auth.date().endsWith("GMT"));
    }
}

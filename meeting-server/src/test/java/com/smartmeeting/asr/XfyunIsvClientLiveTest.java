package com.smartmeeting.asr;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 讯飞 ISV 声纹真网实测（消耗配额，默认不跑）。
 *
 * <p>PowerShell（{@code meeting-server} 目录）：
 * <pre>
 * mvn test "-Dtest=XfyunIsvClientLiveTest" -DskipTests=false "-DXFYUN_LIVE_TEST=true"
 * </pre>
 */
@SpringBootTest
@ActiveProfiles("dev")
@EnabledIfSystemProperty(named = "XFYUN_LIVE_TEST", matches = "true")
class XfyunIsvClientLiveTest {

    private static final Logger log = LoggerFactory.getLogger(XfyunIsvClientLiveTest.class);

    @Autowired
    private XfyunIsvClient xfyunIsvClient;

    @Test
    @DisplayName("实测：queryFeatureList 鉴权 + 拉取声纹库")
    void queryFeatureList_live() {
        List<XfyunIsvClient.FeatureItem> items = xfyunIsvClient.queryFeatureList(null);
        assertNotNull(items);
        log.info("【ISV实测】group={} features={}", xfyunIsvClient.getGroupId(), items.size());
        int preview = Math.min(3, items.size());
        for (int i = 0; i < preview; i++) {
            XfyunIsvClient.FeatureItem item = items.get(i);
            log.info("【ISV实测】feature[{}] id={} info={}", i, item.featureId, item.featureInfo);
        }
    }
}

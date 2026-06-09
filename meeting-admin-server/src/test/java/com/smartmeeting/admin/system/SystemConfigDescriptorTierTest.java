package com.smartmeeting.admin.system;

import com.smartmeeting.config.system.SystemConfigDescriptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 每个系统参数 Descriptor 须明确归属「可热加载」或「冷启动」，不可双 true / 双 false。
 */
@SpringBootTest(classes = SystemConfigDescriptorTierTest.TestConfig.class)
@ActiveProfiles("test")
class SystemConfigDescriptorTierTest {

    @Configuration
    @ComponentScan(basePackages = "com.smartmeeting.admin.system")
    static class TestConfig {
    }

    @Autowired
    private List<SystemConfigDescriptor> descriptors;

    @Test
    void everyDescriptorHasExclusiveReloadTier() {
        assertFalse(descriptors.isEmpty(), "expected SystemConfigDescriptor beans");
        for (SystemConfigDescriptor d : descriptors) {
            boolean cold = d.requiresRestart();
            boolean hot = d.hotReloadable();
            assertNotEquals(hot, cold,
                    d.key() + ": requiresRestart and hotReloadable must be mutually exclusive");
            assertTrue(hot || cold,
                    d.key() + ": must be either hotReloadable or requiresRestart");
        }
    }
}

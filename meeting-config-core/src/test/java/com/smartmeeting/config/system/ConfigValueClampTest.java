package com.smartmeeting.config.system;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigValueClampTest {

    @Test
    void clampInt_clampsToRange() {
        assertEquals(10, ConfigValueClamp.clampInt(15, 1, 10));
        assertEquals(1, ConfigValueClamp.clampInt(0, 1, 10));
        assertEquals(5, ConfigValueClamp.clampInt(5, 1, 10));
    }

    @Test
    void effectiveTopK_usesMinAndMax() {
        assertEquals(10, ConfigValueClamp.effectiveTopK(50, 3, 10));
        assertEquals(3, ConfigValueClamp.effectiveTopK(0, 3, 10));
    }
}

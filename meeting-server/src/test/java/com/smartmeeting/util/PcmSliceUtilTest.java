package com.smartmeeting.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PcmSliceUtilTest {

    @TempDir
    Path tempDir;

    @Test
    void slice_extractsMiddlePortion() throws Exception {
        byte[] pcm = new byte[32000];
        for (int i = 0; i < pcm.length; i++) {
            pcm[i] = (byte) (i % 256);
        }
        Path file = tempDir.resolve("test.pcm");
        Files.write(file, pcm);

        byte[] slice = PcmSliceUtil.slice(file.toString(), 500, 1500, 16000);
        assertEquals(32000, slice.length);
    }

    @Test
    void slice_emptyWhenInvalidRange() throws Exception {
        byte[] pcm = new byte[1000];
        Path file = tempDir.resolve("short.pcm");
        Files.write(file, pcm);
        assertEquals(0, PcmSliceUtil.slice(file.toString(), 10, 5, 16000).length);
    }

    @Test
    void byteLengthToMs() {
        assertTrue(PcmSliceUtil.byteLengthToMs(32000, 16000) >= 900);
    }
}

package com.smartmeeting.service;

import com.smartmeeting.config.MeetingAudioProperties;
import com.smartmeeting.enums.AudioQualityStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudioQualityProbeTest {

    private AudioQualityProbe probe;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        MeetingAudioProperties properties = new MeetingAudioProperties();
        properties.setQualityMinDurationMs(500);
        properties.setQualityMinAbsmax(100);
        properties.setQualityMinRms(10.0);
        probe = new AudioQualityProbe(properties);
    }

    @Test
    void detectsSilentPcm() throws Exception {
        Path pcm = tempDir.resolve("silent.pcm");
        Files.write(pcm, new byte[32000]);

        var report = probe.probe(pcm);
        assertEquals(AudioQualityStatus.SILENT, report.getStatus());
        assertFalse(report.usableForAsr());
    }

    @Test
    void acceptsNormalLevelPcm() throws Exception {
        Path pcm = tempDir.resolve("normal.pcm");
        Files.write(pcm, buildTone(16000, 1000, (short) 5000));

        var report = probe.probe(pcm);
        assertEquals(AudioQualityStatus.OK, report.getStatus());
        assertTrue(report.usableForAsr());
    }

    private static byte[] buildTone(int sampleRate, int durationMs, short amplitude) {
        int samples = sampleRate * durationMs / 1000;
        ByteBuffer buffer = ByteBuffer.allocate(samples * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < samples; i++) {
            buffer.putShort(amplitude);
        }
        return buffer.array();
    }
}

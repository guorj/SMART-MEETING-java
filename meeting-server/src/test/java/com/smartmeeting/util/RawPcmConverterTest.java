package com.smartmeeting.util;

import com.smartmeeting.model.AudioFormatDescriptor;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RawPcmConverterTest {

    @Test
    void converts48kStereoTo16kMonoWithExpectedDuration() {
        byte[] stereo48k = buildStereoTone(48000, 2, 1000, (short) 8000);
        AudioFormatDescriptor source = AudioFormatDescriptor.builder()
                .encoding(AudioFormatDescriptor.ENCODING_PCM_S16LE)
                .sampleRate(48000)
                .channels(2)
                .bitDepth(16)
                .build();

        byte[] normalized = RawPcmConverter.convertBytes(stereo48k, source);

        int durationMs = com.smartmeeting.util.PcmSliceUtil.byteLengthToMs(
                normalized.length, RawPcmConverter.TARGET_SAMPLE_RATE);
        assertTrue(durationMs >= 950 && durationMs <= 1050, "durationMs=" + durationMs);
        assertEquals(0, normalized.length % 2);
    }

    @Test
    void keeps16kMonoUnchangedLength() {
        byte[] mono16k = buildMonoTone(16000, 500, (short) 5000);
        AudioFormatDescriptor source = AudioFormatDescriptor.builder()
                .encoding(AudioFormatDescriptor.ENCODING_PCM_S16LE)
                .sampleRate(16000)
                .channels(1)
                .bitDepth(16)
                .build();

        byte[] normalized = RawPcmConverter.convertBytes(mono16k, source);
        assertEquals(mono16k.length, normalized.length);
    }

    private static byte[] buildMonoTone(int sampleRate, int durationMs, short amplitude) {
        int samples = sampleRate * durationMs / 1000;
        ByteBuffer buffer = ByteBuffer.allocate(samples * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < samples; i++) {
            buffer.putShort(amplitude);
        }
        return buffer.array();
    }

    private static byte[] buildStereoTone(int sampleRate, int channels, int durationMs, short amplitude) {
        int frames = sampleRate * durationMs / 1000;
        ByteBuffer buffer = ByteBuffer.allocate(frames * channels * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < frames; i++) {
            for (int c = 0; c < channels; c++) {
                buffer.putShort(amplitude);
            }
        }
        return buffer.array();
    }
}

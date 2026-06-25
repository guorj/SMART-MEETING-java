package com.smartmeeting.util;

import com.smartmeeting.model.AudioFormatDescriptor;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 原始 PCM 转标准 16kHz / mono / s16le（不依赖 ffmpeg）。
 */
public final class RawPcmConverter {

    public static final int TARGET_SAMPLE_RATE = 16000;
    public static final int TARGET_CHANNELS = 1;
    public static final int TARGET_BIT_DEPTH = 16;

    private RawPcmConverter() {
    }

    public static byte[] convertFile(Path input, AudioFormatDescriptor source) throws IOException {
        byte[] raw = Files.readAllBytes(input);
        return convertBytes(raw, source);
    }

    public static byte[] convertBytes(byte[] raw, AudioFormatDescriptor source) {
        if (raw == null || raw.length == 0 || source == null) {
            return new byte[0];
        }
        if (!AudioFormatDescriptor.ENCODING_PCM_S16LE.equalsIgnoreCase(source.getEncoding())
                && source.getBitDepth() != 16) {
            throw new IllegalArgumentException("Unsupported raw PCM encoding for Java converter: "
                    + source.getEncoding() + "/" + source.getBitDepth());
        }
        short[] interleaved = readS16le(raw, source.getChannels());
        short[] mono = downmixToMono(interleaved, source.getChannels());
        short[] resampled = resample(mono, source.getSampleRate(), TARGET_SAMPLE_RATE);
        return writeS16le(resampled);
    }

    public static int estimateDurationMs(long byteLength, AudioFormatDescriptor source) {
        if (source == null || byteLength <= 0) {
            return 0;
        }
        int bps = source.bytesPerSecond();
        return (int) Math.min(Integer.MAX_VALUE, byteLength * 1000L / bps);
    }

    static short[] readS16le(byte[] raw, int channels) {
        int ch = Math.max(1, channels);
        int frameBytes = ch * 2;
        int usable = raw.length - (raw.length % frameBytes);
        int frameCount = usable / frameBytes;
        short[] out = new short[frameCount * ch];
        ByteBuffer buffer = ByteBuffer.wrap(raw, 0, usable).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < out.length; i++) {
            out[i] = buffer.getShort();
        }
        return out;
    }

    static short[] downmixToMono(short[] interleaved, int channels) {
        int ch = Math.max(1, channels);
        if (ch == 1) {
            return interleaved.clone();
        }
        int frames = interleaved.length / ch;
        short[] mono = new short[frames];
        for (int i = 0; i < frames; i++) {
            int sum = 0;
            for (int c = 0; c < ch; c++) {
                sum += interleaved[i * ch + c];
            }
            mono[i] = (short) (sum / ch);
        }
        return mono;
    }

    static short[] resample(short[] mono, int sourceRate, int targetRate) {
        if (mono.length == 0 || sourceRate <= 0 || targetRate <= 0) {
            return new short[0];
        }
        if (sourceRate == targetRate) {
            return mono.clone();
        }
        int targetLength = Math.max(1, (int) ((long) mono.length * targetRate / sourceRate));
        short[] out = new short[targetLength];
        double ratio = (double) sourceRate / targetRate;
        for (int i = 0; i < targetLength; i++) {
            double srcPos = i * ratio;
            int idx = (int) srcPos;
            double frac = srcPos - idx;
            short s0 = mono[Math.min(idx, mono.length - 1)];
            short s1 = mono[Math.min(idx + 1, mono.length - 1)];
            out[i] = (short) Math.round(s0 * (1.0 - frac) + s1 * frac);
        }
        return out;
    }

    static byte[] writeS16le(short[] samples) {
        ByteBuffer buffer = ByteBuffer.allocate(samples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (short sample : samples) {
            buffer.putShort(sample);
        }
        return buffer.array();
    }
}

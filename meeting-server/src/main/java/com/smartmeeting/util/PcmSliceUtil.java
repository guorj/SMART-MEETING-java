package com.smartmeeting.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 从 16 kHz / 16 bit / mono PCM 文件中按时间范围截取字节。
 */
public final class PcmSliceUtil {

    public static final int DEFAULT_SAMPLE_RATE = 16000;
    public static final int BYTES_PER_SAMPLE = 2;

    private PcmSliceUtil() {
    }

    /**
     * 按起止时间（毫秒）截取 PCM；越界部分 clamp 到文件末尾。
     *
     * @param pcmPath      本地 PCM 路径
     * @param startMs      起始毫秒（含）
     * @param endMs        结束毫秒（含）
     * @param sampleRateHz 采样率，默认 16000
     * @return 截取字节；文件不存在或范围无效时返回空数组
     */
    public static byte[] slice(String pcmPath, int startMs, int endMs, int sampleRateHz) throws IOException {
        if (pcmPath == null || pcmPath.isBlank() || startMs < 0 || endMs <= startMs) {
            return new byte[0];
        }
        Path path = Paths.get(pcmPath);
        if (!Files.exists(path)) {
            return new byte[0];
        }
        byte[] all = Files.readAllBytes(path);
        int rate = sampleRateHz > 0 ? sampleRateHz : DEFAULT_SAMPLE_RATE;
        int startByte = Math.max(0, msToByteOffset(startMs, rate));
        int endByte = Math.min(all.length, msToByteOffset(endMs, rate));
        if (endByte <= startByte) {
            return new byte[0];
        }
        int len = endByte - startByte;
        if ((len & 1) != 0) {
            len--;
        }
        if (len <= 0) {
            return new byte[0];
        }
        byte[] out = new byte[len];
        System.arraycopy(all, startByte, out, 0, len);
        return out;
    }

    /**
     * 将 PCM 字节时长转为毫秒。
     */
    public static int byteLengthToMs(int byteLength, int sampleRateHz) {
        int rate = sampleRateHz > 0 ? sampleRateHz : DEFAULT_SAMPLE_RATE;
        int samples = byteLength / BYTES_PER_SAMPLE;
        return (int) (samples * 1000L / rate);
    }

    private static int msToByteOffset(int ms, int sampleRateHz) {
        long samples = (long) ms * sampleRateHz / 1000L;
        return (int) Math.min(Integer.MAX_VALUE, samples * (long) BYTES_PER_SAMPLE);
    }
}

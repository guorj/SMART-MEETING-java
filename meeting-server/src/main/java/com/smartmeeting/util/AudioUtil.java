package com.smartmeeting.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * 音频 PCM 数据处理工具类。
 * <p>
 * 提供字节与采样点转换、时长计算、流读取及文件写入等基础操作。
 */
public class AudioUtil {

    /**
     * 将 PCM 16-bit 小端字节数组转换为 short 采样点数组。
     *
     * @param pcmData PCM 原始字节（长度须为偶数）
     * @return 采样点数组，长度为 {@code pcmData.length / 2}
     */
    public static short[] bytesToShorts(byte[] pcmData) {
        ByteBuffer buffer = ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN);
        short[] samples = new short[pcmData.length / 2];
        buffer.asShortBuffer().get(samples);
        return samples;
    }

    /**
     * 根据采样点数与采样率计算音频时长（秒）。
     *
     * @param sampleCount 采样点总数
     * @param sampleRate  采样率（Hz），如 16000
     * @return 时长（秒，浮点）
     */
    public static double calculateDuration(int sampleCount, int sampleRate) {
        return (double) sampleCount / sampleRate;
    }

    /**
     * 从输入流读取全部字节。
     *
     * @param is 输入流
     * @return 读取到的完整字节数组
     * @throws IOException 读取失败时抛出
     */
    public static byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[4096];
        int nRead;
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }

    /**
     * 将字节数据写入文件（自动创建父目录）。
     *
     * @param data     待写入字节
     * @param filePath 目标文件路径
     * @throws IOException 目录创建或写入失败时抛出
     */
    public static void writeToFile(byte[] data, String filePath) throws IOException {
        java.nio.file.Path path = Paths.get(filePath);
        Files.createDirectories(path.getParent());
        Files.write(path, data);
    }
}

package com.smartmeeting.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Paths;

public class AudioUtil {

    public static short[] bytesToShorts(byte[] pcmData) {
        ByteBuffer buffer = ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN);
        short[] samples = new short[pcmData.length / 2];
        buffer.asShortBuffer().get(samples);
        return samples;
    }

    public static double calculateDuration(int sampleCount, int sampleRate) {
        return (double) sampleCount / sampleRate;
    }

    public static byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[4096];
        int nRead;
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }

    public static void writeToFile(byte[] data, String filePath) throws IOException {
        java.nio.file.Path path = Paths.get(filePath);
        Files.createDirectories(path.getParent());
        Files.write(path, data);
    }
}

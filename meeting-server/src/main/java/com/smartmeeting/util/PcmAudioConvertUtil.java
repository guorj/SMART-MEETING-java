package com.smartmeeting.util;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 会议录音 PCM（s16le / 16kHz / mono）转 MP3；内部先写 WAV，再调用本机 ffmpeg。
 */
public final class PcmAudioConvertUtil {

    public static final int DEFAULT_SAMPLE_RATE = 16000;
    public static final int DEFAULT_CHANNELS = 1;

    private PcmAudioConvertUtil() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("用法: PcmAudioConvertUtil <input.pcm> [output.mp3]");
            System.exit(1);
        }
        Path pcm = Path.of(args[0]).toAbsolutePath().normalize();
        if (!Files.isRegularFile(pcm)) {
            throw new IllegalArgumentException("PCM 不存在: " + pcm);
        }
        Path mp3 = args.length >= 2
                ? Path.of(args[1]).toAbsolutePath().normalize()
                : pcm.resolveSibling(stem(pcm.getFileName().toString()) + ".mp3");
        Path result = convertPcmToMp3(pcm, mp3, DEFAULT_SAMPLE_RATE, DEFAULT_CHANNELS);
        System.out.println("已生成: " + result);
    }

    /**
     * @return 最终 MP3 路径
     */
    public static Path convertPcmToMp3(Path pcmPath, Path mp3Path, int sampleRate, int channels)
            throws IOException, InterruptedException {
        byte[] pcm = Files.readAllBytes(pcmPath);
        if (pcm.length == 0) {
            throw new IllegalArgumentException("PCM 为空: " + pcmPath);
        }
        Path wav = mp3Path.resolveSibling(stem(mp3Path.getFileName().toString()) + ".tmp.wav");
        try {
            writePcmAsWav(pcm, wav, sampleRate, channels);
            String ffmpeg = resolveFfmpegExecutable();
            List<String> cmd = List.of(
                    ffmpeg,
                    "-y",
                    "-hide_banner",
                    "-loglevel", "error",
                    "-i", wav.toAbsolutePath().toString(),
                    "-codec:a", "libmp3lame",
                    "-qscale:a", "2",
                    mp3Path.toAbsolutePath().toString()
            );
            runCommand(cmd);
            return mp3Path;
        } finally {
            Files.deleteIfExists(wav);
        }
    }

    static void writePcmAsWav(byte[] pcm, Path wavPath, int sampleRate, int channels) throws IOException {
        AudioFormat format = new AudioFormat(sampleRate, 16, channels, true, false);
        try (InputStream in = new ByteArrayInputStream(pcm);
             AudioInputStream audioStream = new AudioInputStream(in, format, pcm.length / format.getFrameSize())) {
            Files.createDirectories(wavPath.getParent());
            AudioSystem.write(audioStream, AudioFileFormat.Type.WAVE, wavPath.toFile());
        }
    }

    static String resolveFfmpegExecutable() {
        List<String> candidates = new ArrayList<>();
        candidates.add("ffmpeg");
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isBlank()) {
            candidates.add(localAppData + "\\Microsoft\\WinGet\\Links\\ffmpeg.exe");
            candidates.add(localAppData + "\\Microsoft\\WinGet\\Packages\\Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe\\ffmpeg-7.1.1-full_build\\bin\\ffmpeg.exe");
        }
        String programFiles = System.getenv("ProgramFiles");
        if (programFiles != null) {
            candidates.add(programFiles + "\\ffmpeg\\bin\\ffmpeg.exe");
        }
        candidates.add("C:\\ffmpeg\\bin\\ffmpeg.exe");
        candidates.add("D:\\ffmpeg\\bin\\ffmpeg.exe");
        candidates.add("tools\\ffmpeg\\bin\\ffmpeg.exe");
        candidates.add("tools\\ffmpeg\\ffmpeg.exe");

        for (String candidate : candidates) {
            if (isExecutable(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "未找到 ffmpeg。请安装后重试，例如: winget install Gyan.FFmpeg");
    }

    private static boolean isExecutable(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        if (path.equals("ffmpeg")) {
            try {
                Process p = new ProcessBuilder("where", "ffmpeg").redirectErrorStream(true).start();
                return p.waitFor(3, TimeUnit.SECONDS) && p.exitValue() == 0;
            } catch (Exception e) {
                return false;
            }
        }
        Path file = Path.of(path);
        return Files.isRegularFile(file);
    }

    private static void runCommand(List<String> command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes());
        if (!process.waitFor(5, TimeUnit.MINUTES)) {
            process.destroyForcibly();
            throw new IllegalStateException("ffmpeg 超时");
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("ffmpeg 失败 (exit=" + process.exitValue() + "): " + output);
        }
    }

    private static String stem(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }
}

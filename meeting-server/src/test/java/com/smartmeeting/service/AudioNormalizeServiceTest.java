package com.smartmeeting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.config.MeetingAudioProperties;
import com.smartmeeting.enums.AudioQualityStatus;
import com.smartmeeting.enums.AudioSourceType;
import com.smartmeeting.model.AudioFormatDescriptor;
import com.smartmeeting.repository.MeetingAudioAssetMapper;
import com.smartmeeting.util.AudioSidecarReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AudioNormalizeServiceTest {

    @Mock
    private MeetingAudioAssetMapper meetingAudioAssetMapper;

    @TempDir
    Path tempDir;

    private AudioNormalizeService service;

    @BeforeEach
    void setUp() {
        MeetingAudioProperties properties = new MeetingAudioProperties();
        properties.setNormalizeEnabled(true);
        properties.setNormalizedSuffix(".normalized.16k-mono.pcm");
        properties.setQualityMinDurationMs(500);
        properties.setQualityMinAbsmax(100);
        properties.setQualityMinRms(10.0);

        MeetingAudioAssetService assetService = new MeetingAudioAssetService(meetingAudioAssetMapper);
        AudioQualityProbe qualityProbe = new AudioQualityProbe(properties);
        AudioSidecarReader sidecarReader = new AudioSidecarReader(new ObjectMapper());
        FfmpegAudioConverter ffmpegConverter = new FfmpegAudioConverter(properties);
        service = new AudioNormalizeService(
                properties, assetService, qualityProbe, sidecarReader, ffmpegConverter);
    }

    @Test
    void normalizes48kStereoWithSidecarMetadata() throws Exception {
        Path input = tempDir.resolve("mix.pcm");
        Files.write(input, buildStereoTone(48000, 2, 1000, (short) 6000));

        Path sidecar = tempDir.resolve("mix.pcm.meta.json");
        Files.writeString(sidecar, """
                {
                  "encoding": "pcm_s16le",
                  "sampleRate": 48000,
                  "channels": 2,
                  "bitDepth": 16,
                  "sourceType": "STEREO_MIX"
                }
                """);

        var result = service.normalize("meeting-1", input.toString());

        assertTrue(result.usableForAsr(), result.getQuality().getMessage());
        assertNotNull(result.getNormalizedPath());
        Path normalized = Path.of(result.getNormalizedPath());
        assertTrue(Files.exists(normalized));
        assertEquals(AudioQualityStatus.OK, result.getQuality().getStatus());
        verify(meetingAudioAssetMapper, atLeast(2)).insert(any());
    }

    @Test
    void copiesStandard16kMonoWithoutConversion() throws Exception {
        Path input = tempDir.resolve("browser.pcm");
        Files.write(input, buildMonoTone(16000, 1000, (short) 6000));

        var result = service.normalize("meeting-2", input.toString(), AudioFormatDescriptor.builder()
                .encoding(AudioFormatDescriptor.ENCODING_PCM_S16LE)
                .sampleRate(16000)
                .channels(1)
                .bitDepth(16)
                .sourceType(AudioSourceType.MICROPHONE)
                .build());

        assertTrue(result.usableForAsr());
        assertTrue(result.getNormalizedPath().endsWith(".normalized.16k-mono.pcm"));
        assertTrue(Files.exists(Path.of(result.getNormalizedPath())));
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

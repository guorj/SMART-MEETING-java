package com.smartmeeting.asr;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.smartmeeting.entity.TranscriptSegment;
import com.smartmeeting.repository.TranscriptMapper;
import com.smartmeeting.service.OfflineTranscriptVoiceprintService;
import com.smartmeeting.service.TranscriptSegmentHelper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 讯飞 IST v2 离线转写<strong>真网实测</strong>（消耗配额，默认不跑）。
 *
 * <p>样本：{@code data/audio/2026-06-07/fb8999e5-cc5b-4592-b8de-939bbb813455.pcm}
 *
 * <p>启用方式（PowerShell，在 {@code meeting-server} 目录）：
 * <pre>
 * mvn test "-Dtest=XfyunOfflineClientLiveTest" -DskipTests=false "-DXFYUN_LIVE_TEST=true"
 * mvn test "-Dtest=XfyunOfflineClientLiveTest" -DskipTests=false "-DXFYUN_LIVE_TEST=true" "-DXFYUN_PCM_MEETING_ID=8bfb0d98-57b3-426d-b030-29665d633b68"
 * mvn test "-Dtest=XfyunOfflineClientLiveTest#rerun_offlineTranscript_persistToDb" -DskipTests=false "-DXFYUN_LIVE_TEST=true" "-DXFYUN_PCM_MEETING_ID=cf84c357-ae70-4440-bf55-c0981f117098"
 * </pre>
 *
 * <p>转写结果写入 PCM 同目录：
 * {@code fb8999e5-...transcript.txt}（可读全文）、{@code ...transcript.json}（分段 JSON）。
 *
 * <p>凭证沿用 {@code application-dev.yml} 中 {@code meeting.asr.xfyun.*}。
 */
@SpringBootTest
@ActiveProfiles("dev")
@EnabledIfSystemProperty(named = "XFYUN_LIVE_TEST", matches = "true")
class XfyunOfflineClientLiveTest {

    private static final Logger log = LoggerFactory.getLogger(XfyunOfflineClientLiveTest.class);

    static final String DEFAULT_MEETING_ID = "fb8999e5-cc5b-4592-b8de-939bbb813455";

    @Autowired
    private XfyunOfflineClient xfyunOfflineClient;

    @Autowired
    private OfflineTranscriptVoiceprintService offlineTranscriptVoiceprintService;

    @Autowired
    private TranscriptMapper transcriptMapper;

    @Autowired
    private TranscriptSegmentHelper transcriptSegmentHelper;

    @Test
    @DisplayName("实测：上传 PCM → 轮询 → 解析转写并写文件")
    void transcribe_sampleMeetingPcm_live() throws Exception {
        String meetingId = resolveMeetingId();
        Path pcmPath = resolvePcmPath(meetingId);
        Assumptions.assumeTrue(Files.isRegularFile(pcmPath),
                "样本 PCM 不存在，跳过实测: " + pcmPath.toAbsolutePath());

        byte[] audio = Files.readAllBytes(pcmPath);
        assertTrue(audio.length > 0, "PCM 文件为空");
        log.info("【实测】meetingId={}, 读取 PCM: path={}, bytes={}, estSec≈{}",
                meetingId,
                pcmPath.toAbsolutePath(),
                audio.length,
                audio.length / 32000);

        String pcmName = meetingId + ".pcm";
        List<TranscriptSegment> segments = xfyunOfflineClient.transcribe(audio, pcmName);

        assertFalse(segments.isEmpty(), "讯飞应返回至少一段转写；若为空请查日志 Upload URL 是否含 %2B0800");
        log.info("【实测】转写分段数={}", segments.size());

        Path txtOut = pcmPath.getParent().resolve(meetingId + ".transcript.txt");
        Path jsonOut = pcmPath.getParent().resolve(meetingId + ".transcript.json");
        writeTranscriptFiles(meetingId, segments, pcmPath, txtOut, jsonOut);
        log.info("【实测】转写已写入: {}", txtOut.toAbsolutePath());
        log.info("【实测】分段 JSON: {}", jsonOut.toAbsolutePath());

        int preview = Math.min(3, segments.size());
        for (int i = 0; i < preview; i++) {
            TranscriptSegment seg = segments.get(i);
            log.info("【实测】segment[{}] speaker={} text={}",
                    i,
                    seg.getSpeakerName(),
                    truncate(seg.getText(), 120));
        }
    }

    @Test
    @DisplayName("重跑：删库旧分段 → 离线转写入库 → 写 transcript 文件")
    void rerun_offlineTranscript_persistToDb() throws Exception {
        String meetingId = resolveMeetingId();
        Path pcmPath = resolvePcmPath(meetingId);
        Assumptions.assumeTrue(Files.isRegularFile(pcmPath),
                "样本 PCM 不存在，跳过实测: " + pcmPath.toAbsolutePath());

        transcriptMapper.delete(new LambdaQueryWrapper<TranscriptSegment>()
                .eq(TranscriptSegment::getMeetingId, meetingId));
        log.info("【重跑】已清除旧分段: meetingId={}", meetingId);

        String labeled = offlineTranscriptVoiceprintService.run(meetingId, pcmPath.toString());
        assertFalse(labeled.isBlank(), "离线转写应返回非空全文");

        List<TranscriptSegment> persisted = transcriptSegmentHelper.listFinalSegments(meetingId);
        assertFalse(persisted.isEmpty(), "库内应有转写分段");
        assertTrue(persisted.stream().anyMatch(s -> s.getEndTimeMs() != null && s.getEndTimeMs() > 0),
                "至少一段应有有效 end_time_ms");

        Path txtOut = pcmPath.getParent().resolve(meetingId + ".transcript.txt");
        Path jsonOut = pcmPath.getParent().resolve(meetingId + ".transcript.json");
        writeTranscriptFiles(meetingId, persisted, pcmPath, txtOut, jsonOut);
        log.info("【重跑】入库 {} 段，transcript: {}", persisted.size(), txtOut.toAbsolutePath());

        for (int i = 0; i < Math.min(3, persisted.size()); i++) {
            TranscriptSegment seg = persisted.get(i);
            log.info("【重跑】segment[{}] {}-{}ms speakerId={} speakerName={} text={}",
                    i,
                    seg.getStartTimeMs(),
                    seg.getEndTimeMs(),
                    seg.getSpeakerId(),
                    seg.getSpeakerName(),
                    truncate(seg.getText(), 80));
        }
    }

    static void writeTranscriptFiles(String meetingId, List<TranscriptSegment> segments, Path pcmPath,
                                     Path txtOut, Path jsonOut) throws Exception {
        Files.createDirectories(txtOut.getParent());
        Files.writeString(txtOut, formatTranscriptText(meetingId, segments, pcmPath), StandardCharsets.UTF_8);

        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        List<Map<String, Object>> rows = segments.stream()
                .sorted(Comparator.comparingInt(s -> s.getStartTimeMs() == null ? 0 : s.getStartTimeMs()))
                .map(seg -> Map.<String, Object>of(
                        "startTimeMs", seg.getStartTimeMs() != null ? seg.getStartTimeMs() : 0,
                        "endTimeMs", seg.getEndTimeMs() != null ? seg.getEndTimeMs() : 0,
                        "speakerName", seg.getSpeakerName() != null ? seg.getSpeakerName() : "",
                        "speakerId", seg.getSpeakerId() != null ? seg.getSpeakerId() : "",
                        "text", seg.getText() != null ? seg.getText() : "",
                        "confidence", seg.getConfidence() != null ? seg.getConfidence() : 0.0
                ))
                .toList();
        mapper.writeValue(jsonOut.toFile(), Map.of(
                "meetingId", meetingId,
                "sourcePcm", pcmPath.getFileName().toString(),
                "segmentCount", segments.size(),
                "segments", rows
        ));
    }

    static String formatTranscriptText(String meetingId, List<TranscriptSegment> segments, Path pcmPath) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 离线转写结果\n");
        sb.append("meetingId: ").append(meetingId).append('\n');
        sb.append("source: ").append(pcmPath.getFileName()).append('\n');
        sb.append("segments: ").append(segments.size()).append("\n\n");
        segments.stream()
                .sorted(Comparator.comparingInt(s -> s.getStartTimeMs() == null ? 0 : s.getStartTimeMs()))
                .forEach(seg -> {
                    sb.append('[').append(formatMs(seg.getStartTimeMs()))
                            .append(" - ").append(formatMs(seg.getEndTimeMs())).append("] ");
                    String speaker = seg.getSpeakerName();
                    if (speaker != null && !speaker.isBlank()) {
                        sb.append(speaker).append(": ");
                    }
                    sb.append(seg.getText() != null ? seg.getText().trim() : "").append('\n');
                });
        return sb.toString().trim() + '\n';
    }

    private static String formatMs(Integer ms) {
        if (ms == null || ms < 0) {
            return "00:00";
        }
        int totalSec = ms / 1000;
        int min = totalSec / 60;
        int sec = totalSec % 60;
        return String.format("%02d:%02d", min, sec);
    }

    static String resolveMeetingId() {
        String id = System.getProperty("XFYUN_PCM_MEETING_ID");
        return (id != null && !id.isBlank()) ? id.trim() : DEFAULT_MEETING_ID;
    }

    static Path resolvePcmPath(String meetingId) {
        String relName = meetingId + ".pcm";
        List<Path> candidates = List.of(
                Path.of("data/audio/2026-06-08", relName),
                Path.of("data/audio/2026-06-07", relName),
                Path.of("meeting-server/data/audio/2026-06-08", relName),
                Path.of("meeting-server/data/audio/2026-06-07", relName),
                Path.of("data/audio", relName)
        );
        for (Path p : candidates) {
            if (Files.isRegularFile(p)) {
                return p.toAbsolutePath().normalize();
            }
        }
        Path audioRoot = Path.of("data/audio");
        if (Files.isDirectory(audioRoot)) {
            try (var stream = Files.list(audioRoot)) {
                var found = stream
                        .filter(Files::isDirectory)
                        .map(d -> d.resolve(relName))
                        .filter(Files::isRegularFile)
                        .findFirst();
                if (found.isPresent()) {
                    return found.get().toAbsolutePath().normalize();
                }
            } catch (IOException ignored) {
                // fall through
            }
        }
        Path serverAudioRoot = Path.of("meeting-server/data/audio");
        if (Files.isDirectory(serverAudioRoot)) {
            try (var stream = Files.list(serverAudioRoot)) {
                var found = stream
                        .filter(Files::isDirectory)
                        .map(d -> d.resolve(relName))
                        .filter(Files::isRegularFile)
                        .findFirst();
                if (found.isPresent()) {
                    return found.get().toAbsolutePath().normalize();
                }
            } catch (IOException ignored) {
                // fall through
            }
        }
        return Path.of("data/audio", relName).toAbsolutePath().normalize();
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }
}

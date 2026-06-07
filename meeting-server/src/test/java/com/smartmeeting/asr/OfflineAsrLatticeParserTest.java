package com.smartmeeting.asr;

import com.smartmeeting.entity.TranscriptSegment;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfflineAsrLatticeParserTest {

    @Test
    void parse_extractsRolesAndTimeMs() throws Exception {
        String json = Files.readString(
                Path.of("src/test/resources/xfyun/offline-lattice-sample.json"),
                StandardCharsets.UTF_8);
        List<TranscriptSegment> segments = OfflineAsrLatticeParser.parse(json);
        assertEquals(2, segments.size());
        Set<String> speakers = segments.stream().map(TranscriptSegment::getSpeakerId).collect(Collectors.toSet());
        assertTrue(speakers.contains("speaker_1"));
        assertTrue(speakers.contains("speaker_2"));
        assertEquals(0, segments.get(0).getStartTimeMs());
        assertEquals(1500, segments.get(0).getEndTimeMs());
        assertEquals("你好", segments.get(0).getText());
    }
}

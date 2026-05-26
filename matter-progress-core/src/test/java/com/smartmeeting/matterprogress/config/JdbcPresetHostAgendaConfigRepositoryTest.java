package com.smartmeeting.matterprogress.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.config.agenda.HostAgendaItem;
import com.smartmeeting.config.agenda.HostAgendaJsonCodec;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcPresetHostAgendaConfigRepositoryTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void updateGeneratedReport_roundTripInJson() {
        String json = """
                {"version":2,"items":[{"title":"会序1","docs":[{"configName":"out-1","role":"OUTPUT","slot":0,"enabled":true}]}]}
                """;
        String updated = HostAgendaJsonCodec.updateGeneratedReport(
                MAPPER, json, "out-1", "https://x.feishu.cn/docx/report", LocalDateTime.of(2026, 5, 24, 10, 0));
        assertTrue(updated.contains("generatedReportUrl"));
        assertTrue(updated.contains("https://x.feishu.cn/docx/report"));
        HostAgendaItem item = HostAgendaJsonCodec.parseItems(MAPPER, updated).get(0);
        assertTrue(item.getDocs().get(0).getGeneratedReportUrl().contains("report"));
    }
}

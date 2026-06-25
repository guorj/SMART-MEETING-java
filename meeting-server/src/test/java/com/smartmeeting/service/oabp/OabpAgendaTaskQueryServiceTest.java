package com.smartmeeting.service.oabp;

import org.junit.jupiter.api.Test;

import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OabpAgendaTaskQueryServiceTest {

    @Test
    void formatCell_nullAndText() {
        assertEquals("", OabpAgendaTaskQueryService.formatCell(null));
        assertEquals("任务A", OabpAgendaTaskQueryService.formatCell("任务A"));
        assertEquals("85", OabpAgendaTaskQueryService.formatCell(85));
    }

    @Test
    void formatCell_sqlDate() {
        assertEquals("2026-06-25", OabpAgendaTaskQueryService.formatCell(java.sql.Date.valueOf("2026-06-25")));
    }

    @Test
    void formatCell_timestampAndLocalDateTime() {
        assertEquals(
                "2026-06-25 14:30:00",
                OabpAgendaTaskQueryService.formatCell(Timestamp.valueOf("2026-06-25 14:30:00")));
        assertEquals(
                "2026-06-25 14:30:00",
                OabpAgendaTaskQueryService.formatCell(LocalDateTime.of(2026, 6, 25, 14, 30, 0)));
        assertEquals("2026-06-25", OabpAgendaTaskQueryService.formatCell(LocalDate.of(2026, 6, 25)));
    }

    @Test
    void formatCell_sqlTime() {
        assertEquals("09:15:00", OabpAgendaTaskQueryService.formatCell(Time.valueOf("09:15:00")));
    }

    @Test
    void formatCell_bitAndBoolean() {
        assertEquals("1", OabpAgendaTaskQueryService.formatCell(true));
        assertEquals("0", OabpAgendaTaskQueryService.formatCell(false));
        assertEquals("0", OabpAgendaTaskQueryService.formatCell(new byte[] {0}));
        assertEquals("1", OabpAgendaTaskQueryService.formatCell(new byte[] {1}));
    }
}

package com.smartmeeting.config.agenda;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PresetScheduleConfigCodecTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void weeklyPreferNextWhenPastUsesNextWeek() {
        LocalDateTime ref = LocalDateTime.of(2026, 6, 12, 15, 0); // Friday 15:00
        String json = "{\"type\":\"weekly\",\"weekday\":1,\"hour\":10,\"minute\":0,\"preferNextIfPast\":true}";
        LocalDateTime resolved = PresetScheduleConfigCodec.resolve(mapper, json, ref);
        LocalDateTime expectedMonday = LocalDateTime.of(
                ref.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                LocalTime.of(10, 0)).plusWeeks(1);
        assertEquals(expectedMonday, resolved);
    }

    @Test
    void weeklySameWeekWhenNotPast() {
        LocalDateTime ref = LocalDateTime.of(2026, 6, 12, 9, 0); // Friday 09:00
        String json = "{\"type\":\"weekly\",\"weekday\":5,\"hour\":10,\"minute\":0,\"preferNextIfPast\":true}";
        LocalDateTime resolved = PresetScheduleConfigCodec.resolve(mapper, json, ref);
        LocalDate expectedFriday = ref.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.FRIDAY));
        assertEquals(LocalDateTime.of(expectedFriday, LocalTime.of(10, 0)), resolved);
    }

    @Test
    void atStartUsesReferenceTime() {
        LocalDateTime ref = LocalDateTime.of(2026, 6, 12, 15, 30);
        LocalDateTime resolved = PresetScheduleConfigCodec.resolve(
                mapper, "{\"type\":\"at_start\"}", ref);
        assertEquals(ref, resolved);
    }

    @Test
    void nullConfigFallsBackToReferenceTime() {
        LocalDateTime ref = LocalDateTime.of(2026, 6, 12, 15, 30);
        assertEquals(ref, PresetScheduleConfigCodec.resolve(mapper, null, ref));
    }

    @Test
    void validateWeeklyRequiresFields() {
        assertTrue(PresetScheduleConfigCodec.validate(
                PresetScheduleConfig.builder().type("weekly").build()).size() >= 3);
    }
}

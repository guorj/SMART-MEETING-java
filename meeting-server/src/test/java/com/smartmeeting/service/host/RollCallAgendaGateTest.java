package com.smartmeeting.service.host;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link RollCallAgendaGate} 单元测试。
 */
class RollCallAgendaGateTest {

    @Test
    @DisplayName("agendaHasRollCallChapter：标题含检点")
    void agendaHasRollCallChapter_whenTitleContains() {
        assertTrue(RollCallAgendaGate.agendaHasRollCallChapter(List.of("会序1：会议检点")));
        assertTrue(RollCallAgendaGate.agendaHasRollCallChapter(List.of("主持议题A", "检点环节")));
    }

    @Test
    @DisplayName("agendaHasRollCallChapter：无检点会序")
    void agendaHasRollCallChapter_whenMissing() {
        assertFalse(RollCallAgendaGate.agendaHasRollCallChapter(List.of("事项进度通报", "主持议题A")));
        assertFalse(RollCallAgendaGate.agendaHasRollCallChapter(List.of()));
        assertFalse(RollCallAgendaGate.agendaHasRollCallChapter(null));
    }
}

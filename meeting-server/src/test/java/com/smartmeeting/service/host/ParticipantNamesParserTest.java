package com.smartmeeting.service.host;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ParticipantNamesParserTest {

    @Test
    void parsesMixedSeparators() {
        List<String> a = ParticipantNamesParser.parse("张三、李四,王五 赵六");
        assertEquals(List.of("张三", "李四", "王五", "赵六"), a);
    }

    @Test
    void jiAndDedup() {
        List<String> b = ParticipantNamesParser.parse("甲及乙、甲");
        assertEquals(List.of("甲", "乙"), b);
    }
}

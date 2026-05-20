package com.smartmeeting.service.host;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link ParticipantNamesParser} 单元测试：验证参会人姓名解析与去重逻辑。
 */
class ParticipantNamesParserTest {

    /** 应正确解析顿号、逗号、空格等混合分隔符。 */
    @Test
    void parsesMixedSeparators() {
        List<String> a = ParticipantNamesParser.parse("张三、李四,王五 赵六");
        assertEquals(List.of("张三", "李四", "王五", "赵六"), a);
    }

    /** 「甲及乙」应展开为两人并去重。 */
    @Test
    void jiAndDedup() {
        List<String> b = ParticipantNamesParser.parse("甲及乙、甲");
        assertEquals(List.of("甲", "乙"), b);
    }
}

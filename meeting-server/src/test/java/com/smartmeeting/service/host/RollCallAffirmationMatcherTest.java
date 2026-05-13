package com.smartmeeting.service.host;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RollCallAffirmationMatcherTest {

    @Test
    void matchesCommonPhrases() {
        assertTrue(RollCallAffirmationMatcher.matches("答到"));
        assertTrue(RollCallAffirmationMatcher.matches(" 答到 "));
        assertTrue(RollCallAffirmationMatcher.matches("在"));
        assertTrue(RollCallAffirmationMatcher.matches("到"));
        assertTrue(RollCallAffirmationMatcher.matches("到了"));
        assertTrue(RollCallAffirmationMatcher.matches("在的"));
        assertTrue(RollCallAffirmationMatcher.matches("好的"));
        assertTrue(RollCallAffirmationMatcher.matches("嗯"));
        assertTrue(RollCallAffirmationMatcher.matches("我在"));
        assertTrue(RollCallAffirmationMatcher.matches("我到了"));
        assertTrue(RollCallAffirmationMatcher.matches("在呢"));
        assertTrue(RollCallAffirmationMatcher.matches("收到"));
        assertTrue(RollCallAffirmationMatcher.matches("在，"));
    }

    @Test
    void longTextWithDaDaoStillMatchesUnlessPureNameCall() {
        assertTrue(RollCallAffirmationMatcher.matches(
                "那个嗯请靠近麦克风好的答到"));
        assertFalse(RollCallAffirmationMatcher.matches("请张三答到。"));
        assertFalse(RollCallAffirmationMatcher.matches("请李四答到"));
    }

    @Test
    void rejectsLongOrIrrelevant() {
        assertFalse(RollCallAffirmationMatcher.matches(""));
        assertFalse(RollCallAffirmationMatcher.matches("   "));
        assertFalse(RollCallAffirmationMatcher.matches("我们今天讨论一下项目进度和排期问题"));
        assertFalse(RollCallAffirmationMatcher.matches("不知道"));
    }

    @Test
    void rejectsArrivalQuestionsAndThirdPartyStatements() {
        assertFalse(RollCallAffirmationMatcher.matches("他到了吗？李海天"));
        assertFalse(RollCallAffirmationMatcher.matches("李海天到了吗"));
        assertFalse(RollCallAffirmationMatcher.matches("? 李海天到了吗"));
        assertFalse(RollCallAffirmationMatcher.matches("他到了"));
        assertFalse(RollCallAffirmationMatcher.matches("人到了没"));
        assertFalse(RollCallAffirmationMatcher.matches("到没到"));
        assertFalse(RollCallAffirmationMatcher.matches("他答到了吗"));
    }

    @Test
    void stillAcceptsShortSelfAffirmationWithDaoLe() {
        assertTrue(RollCallAffirmationMatcher.matches("到了"));
        assertTrue(RollCallAffirmationMatcher.matches("嗯到了"));
        assertTrue(RollCallAffirmationMatcher.matches("噢到了"));
        assertTrue(RollCallAffirmationMatcher.matches("到了。"));
    }
}

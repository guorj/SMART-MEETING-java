package com.smartmeeting.config.oabp;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OabpFilterEvaluatorTest {

    private static final List<String> HEADERS = List.of("status_code", "task_name", "progress");

    @Test
    void andGroup_matchesAllRules() {
        OabpDisplayFilterNode root = new OabpDisplayFilterNode();
        root.setType("group");
        root.setOp("and");

        OabpDisplayFilterNode r1 = rule("status_code", "!=", "99");
        OabpDisplayFilterNode r2 = rule("progress", "<", "100");
        root.setChildren(List.of(r1, r2));

        assertTrue(OabpFilterEvaluator.matches(root, HEADERS, List.of("0", "A", "50")));
        assertFalse(OabpFilterEvaluator.matches(root, HEADERS, List.of("99", "A", "50")));
    }

    @Test
    void orGroup_matchesAnyRule() {
        OabpDisplayFilterNode root = new OabpDisplayFilterNode();
        root.setType("group");
        root.setOp("or");
        root.setChildren(List.of(
                rule("status_code", "==", "0"),
                rule("status_code", "==", "2")));

        assertTrue(OabpFilterEvaluator.matches(root, HEADERS, List.of("0", "A", "50")));
        assertTrue(OabpFilterEvaluator.matches(root, HEADERS, List.of("2", "B", "10")));
        assertFalse(OabpFilterEvaluator.matches(root, HEADERS, List.of("1", "C", "10")));
    }

    @Test
    void notGroup_negatesChild() {
        OabpDisplayFilterNode root = new OabpDisplayFilterNode();
        root.setType("group");
        root.setOp("not");
        root.setChildren(List.of(rule("status_code", "==", "99")));

        assertFalse(OabpFilterEvaluator.matches(root, HEADERS, List.of("99", "A", "50")));
        assertTrue(OabpFilterEvaluator.matches(root, HEADERS, List.of("0", "A", "50")));
    }

    @Test
    void containsAndIsEmpty() {
        assertTrue(OabpFilterEvaluator.matches(rule("task_name", "contains", "周报"), HEADERS,
                List.of("0", "本周周报", "50")));
        assertTrue(OabpFilterEvaluator.matches(rule("task_name", "is_not_empty", ""), HEADERS,
                List.of("0", "任务", "50")));
        assertFalse(OabpFilterEvaluator.matches(rule("task_name", "is_empty", ""), HEADERS,
                List.of("0", "任务", "50")));
    }

    @Test
    void multiValueContainsAndNotContains() {
        assertTrue(OabpFilterEvaluator.matches(rule("task_name", "contains", "周报,月报"), HEADERS,
                List.of("0", "本周周报", "50")));
        assertTrue(OabpFilterEvaluator.matches(rule("task_name", "contains", "周报,月报"), HEADERS,
                List.of("0", "1月月报", "50")));
        assertFalse(OabpFilterEvaluator.matches(rule("task_name", "contains", "周报,月报"), HEADERS,
                List.of("0", "日常跟进", "50")));

        assertTrue(OabpFilterEvaluator.matches(rule("task_name", "not_contains", "周报,月报"), HEADERS,
                List.of("0", "日常跟进", "50")));
        assertFalse(OabpFilterEvaluator.matches(rule("task_name", "not_contains", "周报,月报"), HEADERS,
                List.of("0", "本周周报", "50")));
        assertFalse(OabpFilterEvaluator.matches(rule("task_name", "not_contains", "周报,月报"), HEADERS,
                List.of("0", "1月月报", "50")));
    }

    @Test
    void inSetWithMultipleValues() {
        assertTrue(OabpFilterEvaluator.matches(rule("status_code", "in", "0,2"), HEADERS,
                List.of("0", "A", "50")));
        assertTrue(OabpFilterEvaluator.matches(rule("status_code", "in", "0,2"), HEADERS,
                List.of("2", "A", "50")));
        assertFalse(OabpFilterEvaluator.matches(rule("status_code", "in", "0,2"), HEADERS,
                List.of("1", "A", "50")));
    }

    private static OabpDisplayFilterNode rule(String field, String op, String value) {
        OabpDisplayFilterNode n = new OabpDisplayFilterNode();
        n.setType("rule");
        n.setField(field);
        n.setOp(op);
        n.setValue(value);
        return n;
    }
}

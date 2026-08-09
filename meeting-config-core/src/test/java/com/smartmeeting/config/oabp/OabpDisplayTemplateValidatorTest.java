package com.smartmeeting.config.oabp;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OabpDisplayTemplateValidatorTest {

    @Test
    void validate_acceptsGroupedTemplate() {
        OabpDisplayTemplate template = OabpDisplayPresetRegistry.copyTemplate("project_task_grouped");
        List<String> headers = List.of("task_name", "status_code", "progress", "plan_date");
        assertTrue(OabpDisplayTemplateValidator.validate(template, headers, 500).isEmpty());
    }

    @Test
    void validate_rejectsUnknownColumn() {
        OabpDisplayColumn col = new OabpDisplayColumn();
        col.setSource("missing_col");
        col.setLabel("缺失");
        col.setVisible(true);
        OabpDisplayTemplate template = new OabpDisplayTemplate();
        template.setColumns(List.of(col));

        List<String> issues = OabpDisplayTemplateValidator.validate(
                template, List.of("task_name"), 500);
        assertFalse(issues.isEmpty());
        assertTrue(issues.stream().anyMatch(s -> s.contains("missing_col")));
    }

    @Test
    void validate_rejectsIllegalDisplayMode() {
        OabpDisplayTemplate template = new OabpDisplayTemplate();
        template.setDisplayMode("cards");
        OabpDisplayColumn col = new OabpDisplayColumn();
        col.setSource("task_name");
        col.setVisible(true);
        template.setColumns(List.of(col));

        List<String> issues = OabpDisplayTemplateValidator.validate(template, List.of("task_name"), 500);
        assertTrue(issues.stream().anyMatch(s -> s.contains("displayMode")));
    }
}

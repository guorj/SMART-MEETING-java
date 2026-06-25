package com.smartmeeting.config.oabp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OabpReadOnlySqlValidatorTest {

    @Test
    void acceptsSelect() {
        assertEquals(
                "SELECT id FROM t WHERE x = 1",
                OabpReadOnlySqlValidator.validateAndNormalize("  SELECT id FROM t WHERE x = 1  "));
    }

    @Test
    void rejectsUpdate() {
        assertThrows(IllegalArgumentException.class,
                () -> OabpReadOnlySqlValidator.validateAndNormalize("UPDATE t SET x=1"));
    }

    @Test
    void validateOptionalBlank() {
        assertNull(OabpReadOnlySqlValidator.validateOptional(null));
        assertNull(OabpReadOnlySqlValidator.validateOptional("  "));
    }

    @Test
    void acceptsPresetSqlWithDeletedColumn() {
        String sql = """
                SELECT t.task_name, t.deleted
                FROM jq_project_task_tracking t
                WHERE t.deleted = 0 AND assignee.deleted IS NULL
                """;
        assertEquals(
                sql.strip().replace("\r\n", "\n"),
                OabpReadOnlySqlValidator.validateAndNormalize(sql));
    }
}

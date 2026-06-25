package com.smartmeeting.service.oabp;

import com.smartmeeting.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OabpReadOnlySqlValidatorTest {

    @Test
    void acceptsSimpleSelect() {
        String sql = "SELECT id, task_name FROM jq_project_task_tracking WHERE project_id = 1";
        assertEquals(sql, OabpReadOnlySqlValidator.validateAndNormalize(sql));
    }

    @Test
    void rejectsEmpty() {
        assertThrows(BusinessException.class, () -> OabpReadOnlySqlValidator.validateAndNormalize("  "));
    }

    @Test
    void rejectsSemicolon() {
        assertThrows(BusinessException.class, () ->
                OabpReadOnlySqlValidator.validateAndNormalize("SELECT 1; DROP TABLE t"));
    }

    @Test
    void rejectsNonSelect() {
        assertThrows(BusinessException.class, () ->
                OabpReadOnlySqlValidator.validateAndNormalize("UPDATE jq_project_task_tracking SET x=1"));
    }

    @Test
    void rejectsForbiddenKeywordInSelect() {
        assertThrows(BusinessException.class, () ->
                OabpReadOnlySqlValidator.validateAndNormalize("SELECT * FROM t; DELETE FROM t"));
    }

    @Test
    void rejectsInsertDisguised() {
        assertThrows(BusinessException.class, () ->
                OabpReadOnlySqlValidator.validateAndNormalize("INSERT INTO t SELECT 1"));
    }
}

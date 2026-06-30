package com.smartmeeting.config.agenda;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostAgendaJsonCodecDocContextTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void findDocContextByConfigName_includesOabpTaskSql() {
        String json = """
                {
                  "version": 2,
                  "items": [
                    {
                      "title": "项目通报",
                      "minutes": 10,
                      "oabpTaskSql": "SELECT id FROM jq_project_task_tracking WHERE deleted = 0",
                      "docs": [
                        {
                          "configName": "preset1-comp-agenda-01",
                          "role": "SOURCE",
                          "enabled": true
                        }
                      ]
                    }
                  ]
                }
                """;
        var ctx = HostAgendaJsonCodec.findDocContextByConfigName(json, "preset1-comp-agenda-01", MAPPER);
        assertTrue(ctx.isPresent());
        assertTrue(ctx.get().oabpTaskSql().contains("jq_project_task_tracking"));
        assertEquals("preset1-comp-agenda-01", ctx.get().doc().getConfigName());
    }
}

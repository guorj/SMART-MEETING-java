package com.smartmeeting.service.oabp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.api.dto.AgendaDocPartDto;
import com.smartmeeting.api.dto.structured.SheetStructuredDto;
import com.smartmeeting.entity.Meeting;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OabpAgendaTaskPartBuilderTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void buildParts_emptyWhenNoSql() {
        OabpAgendaTaskPartBuilder builder = new OabpAgendaTaskPartBuilder(JSON, null);
        Meeting meeting = new Meeting();
        meeting.setHostAgenda("{\"version\":2,\"items\":[{\"title\":\"议题1\"}]}");
        assertTrue(builder.buildParts(meeting, 0).isEmpty());
    }

    @Test
    void buildParts_disabledWhenOabpOff() {
        OabpAgendaTaskPartBuilder builder = new OabpAgendaTaskPartBuilder(JSON, null);
        Meeting meeting = new Meeting();
        meeting.setHostAgenda("""
                {"version":2,"items":[{"title":"议题1","oabpTaskSql":"SELECT 1 AS n"}]}
                """);
        List<AgendaDocPartDto> parts = builder.buildParts(meeting, 0);
        assertEquals(1, parts.size());
        assertEquals("OABP", parts.get(0).getDocKind());
        assertNotNull(parts.get(0).getFetchError());
    }

    @Test
    void buildParts_returnsSheetWhenQuerySucceeds() {
        OabpAgendaTaskQueryService queryService = mock(OabpAgendaTaskQueryService.class);
        SheetStructuredDto sheet = SheetStructuredDto.builder()
                .sheetName("项目任务")
                .headers(List.of("task_name", "status"))
                .rows(List.of(List.of("任务A", "进行中")))
                .headerRowCount(1)
                .build();
        when(queryService.queryAsSheet(anyString())).thenReturn(sheet);

        OabpAgendaTaskPartBuilder builder = new OabpAgendaTaskPartBuilder(JSON, queryService);
        Meeting meeting = new Meeting();
        meeting.setHostAgenda("""
                {"version":2,"items":[{"title":"议题1","oabpTaskSql":"SELECT task_name, status FROM jq_project_task_tracking"}]}
                """);
        List<AgendaDocPartDto> parts = builder.buildParts(meeting, 0);
        assertEquals(1, parts.size());
        assertEquals("sheet_cells", parts.get(0).getContentType());
        assertNotNull(parts.get(0).getStructuredContent());
    }

    @Test
    void buildParts_appliesDisplayTemplate() {
        OabpAgendaTaskQueryService queryService = mock(OabpAgendaTaskQueryService.class);
        SheetStructuredDto sheet = SheetStructuredDto.builder()
                .sheetName("项目任务")
                .headers(List.of("task_name", "status_code"))
                .rows(List.of(List.of("任务A", "0")))
                .headerRowCount(1)
                .build();
        when(queryService.queryAsSheet(anyString())).thenReturn(sheet);

        OabpAgendaTaskPartBuilder builder = new OabpAgendaTaskPartBuilder(JSON, queryService);
        Meeting meeting = new Meeting();
        meeting.setHostAgenda("""
                {"version":2,"items":[{"title":"议题1","oabpTaskSql":"SELECT task_name, status_code FROM jq_project_task_tracking","oabpDisplayTemplate":{"displayMode":"table","columns":[{"source":"task_name","label":"任务","visible":true}]}}]}
                """);
        List<AgendaDocPartDto> parts = builder.buildParts(meeting, 0);
        assertEquals(1, parts.size());
        SheetStructuredDto out = (SheetStructuredDto) parts.get(0).getStructuredContent();
        assertEquals(List.of("任务"), out.getHeaders());
        assertEquals(List.of("任务A"), out.getRows().get(0));
        assertNotNull(out.getDisplayMeta());
    }

    @Test
    void buildParts_skipsDisplayTemplateWhenSqlStrict() {
        OabpAgendaTaskQueryService queryService = mock(OabpAgendaTaskQueryService.class);
        SheetStructuredDto sheet = SheetStructuredDto.builder()
                .sheetName("项目任务")
                .headers(List.of("task_name", "status_code"))
                .rows(List.of(List.of("任务A", "0")))
                .headerRowCount(1)
                .build();
        when(queryService.queryAsSheet(anyString())).thenReturn(sheet);

        OabpAgendaTaskPartBuilder builder = new OabpAgendaTaskPartBuilder(JSON, queryService);
        Meeting meeting = new Meeting();
        meeting.setHostAgenda("""
                {"version":2,"items":[{"title":"议题1","oabpTaskSql":"SELECT task_name, status_code FROM jq_project_task_tracking","oabpTaskSqlStrict":true,"oabpDisplayTemplate":{"displayMode":"table","columns":[{"source":"task_name","label":"任务","visible":true}]}}]}
                """);
        List<AgendaDocPartDto> parts = builder.buildParts(meeting, 0);
        assertEquals(1, parts.size());
        SheetStructuredDto out = (SheetStructuredDto) parts.get(0).getStructuredContent();
        assertEquals(List.of("task_name", "status_code"), out.getHeaders());
        assertNotNull(out.getDisplayMeta());
        assertEquals(Boolean.TRUE, out.getDisplayMeta().getSqlStrict());
    }

    @Test
    void buildParts_emptyWhenShowDisabled() {
        OabpAgendaTaskQueryService queryService = mock(OabpAgendaTaskQueryService.class);
        OabpAgendaTaskPartBuilder builder = new OabpAgendaTaskPartBuilder(JSON, queryService);
        Meeting meeting = new Meeting();
        meeting.setHostAgenda("""
                {"version":2,"items":[{"title":"议题1","oabpTaskSql":"SELECT 1 AS n","oabpTaskShow":false}]}
                """);
        assertTrue(builder.buildParts(meeting, 0).isEmpty());
    }
}

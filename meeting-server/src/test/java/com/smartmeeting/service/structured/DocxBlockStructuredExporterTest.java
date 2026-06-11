package com.smartmeeting.service.structured;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.api.dto.structured.DocxBlockDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocxBlockStructuredExporterTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void exportsHeadingWithRuns() throws Exception {
        var items = mapper.readTree("""
                [{"block_id":"p1","block_type":1,"children":["h1"]},
                 {"block_id":"h1","parent_id":"p1","block_type":3,"heading1":{"elements":[{"text_run":{"content":"标题","text_element_style":{"bold":true}}}]}}]
                """);
        List<DocxBlockDto> blocks = DocxBlockStructuredExporter.exportBlocks(items);
        assertEquals(1, blocks.size());
        assertEquals("heading1", blocks.get(0).getType());
        assertEquals("标题", blocks.get(0).getText());
        assertNotNull(blocks.get(0).getRuns());
        assertTrue(blocks.get(0).getRuns().get(0).getBold());
    }

    @Test
    void exportsNestedBulletChildren() throws Exception {
        var items = mapper.readTree("""
                [{"block_id":"p1","block_type":1,"children":["b1"]},
                 {"block_id":"b1","parent_id":"p1","block_type":12,"children":["b2"],"bullet":{"elements":[{"text_run":{"content":"外层"}}]}},
                 {"block_id":"b2","parent_id":"b1","block_type":12,"bullet":{"elements":[{"text_run":{"content":"内层"}}]}}]
                """);
        List<DocxBlockDto> blocks = DocxBlockStructuredExporter.exportBlocks(items);
        assertEquals(1, blocks.size());
        assertEquals("bullet", blocks.get(0).getType());
        assertNotNull(blocks.get(0).getChildren());
        assertEquals(1, blocks.get(0).getChildren().size());
        assertEquals("内层", blocks.get(0).getChildren().get(0).getText());
    }

    @Test
    void exportsCodeBlock() throws Exception {
        var items = mapper.readTree("""
                [{"block_id":"c1","block_type":14,"code":{"elements":[{"text_run":{"content":"println()"}}]}}]
                """);
        List<DocxBlockDto> blocks = DocxBlockStructuredExporter.exportBlocks(items);
        assertEquals(1, blocks.size());
        assertEquals("code", blocks.get(0).getType());
        assertEquals("println()", blocks.get(0).getText());
    }
}

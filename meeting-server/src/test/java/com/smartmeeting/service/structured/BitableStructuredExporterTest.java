package com.smartmeeting.service.structured;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.api.dto.structured.BitableStructuredDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BitableStructuredExporterTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void exportsRecordsAndColumns() throws Exception {
        var record = mapper.readTree("""
                {"fields":{"任务":"完成设计","负责人":[{"name":"张三"}],"进度":0.8}}
                """);
        BitableStructuredDto dto = BitableStructuredExporter.export(List.of(record), "任务表");
        assertNotNull(dto);
        assertEquals("任务表", dto.getTableName());
        assertEquals(1, dto.getRecords().size());
        assertTrue(dto.getColumns().stream().anyMatch(c -> "任务".equals(c.getName())));
    }

    @Test
    void groupedModeBuildsSectionGroups() throws Exception {
        var record = mapper.readTree("""
                {"fields":{"任务":"进行中项","状态":"进行中"},"created_time":1710000000000}
                """);
        var dto = BitableStructuredExporter.export(List.of(record), "任务表",
                com.smartmeeting.matterprogress.feishu.BitableDisplayMode.GROUPED);
        assertNotNull(dto.getGroups());
        assertTrue(dto.getGroups().stream().anyMatch(g -> "_section".equals(g.getField())));
    }
}

package com.smartmeeting.service.structured;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.api.dto.structured.TaskListStructuredDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskListStructuredExporterTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void exportsTaskListFromApiJson() throws Exception {
        var tasklist = mapper.readTree("""
                {
                  "guid": "3debd4f2-1f74-4e8c-89a1-e43be74b0dc4",
                  "name": "项目跟进清单",
                  "url": "https://applink.feishu.cn/client/todo/task_list?guid=3debd4f2-1f74-4e8c-89a1-e43be74b0dc4"
                }
                """);
        var itemOpen = mapper.readTree("""
                {
                  "guid": "t-open-01",
                  "summary": "完成方案评审",
                  "completed_at": "0",
                  "due": { "timestamp": "1780272000000", "is_all_day": false },
                  "members": [{ "id": "ou_assignee", "type": "user", "role": "assignee" }]
                }
                """);
        var itemDone = mapper.readTree("""
                {
                  "guid": "t-done-01",
                  "summary": "提交周报",
                  "completed_at": "1700000000000",
                  "due": { "timestamp": "0", "is_all_day": false },
                  "members": []
                }
                """);
        TaskListStructuredDto dto = TaskListStructuredExporter.export(
                tasklist, List.of(itemOpen, itemDone), tasklist.path("url").asText());
        assertEquals("项目跟进清单", dto.getTasklistName());
        assertEquals(2, dto.getTotalTasks());
        assertEquals("in_progress", dto.getItems().get(0).getStatus());
        assertEquals("completed", dto.getItems().get(1).getStatus());
        assertEquals("ou_assignee", dto.getItems().get(0).getAssignees());
        assertTrue(dto.getItems().get(0).getDueAt().contains("2026"));
        String plain = TaskListStructuredExporter.exportPlainText(dto);
        assertTrue(plain.contains("项目跟进清单"));
        assertTrue(plain.contains("完成方案评审"));
    }
}

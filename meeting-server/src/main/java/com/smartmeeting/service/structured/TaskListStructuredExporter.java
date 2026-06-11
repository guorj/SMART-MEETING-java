package com.smartmeeting.service.structured;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartmeeting.api.dto.structured.TaskListItemDto;
import com.smartmeeting.api.dto.structured.TaskListStructuredDto;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TaskListStructuredExporter {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private TaskListStructuredExporter() {
    }

    public static TaskListStructuredDto export(JsonNode tasklistNode, List<JsonNode> taskItems, String openUrl) {
        String guid = tasklistNode != null ? tasklistNode.path("guid").asText("").trim() : "";
        String name = tasklistNode != null ? tasklistNode.path("name").asText("").trim() : "";
        if (name.isEmpty()) {
            name = "任务清单";
        }
        List<TaskListItemDto> items = new ArrayList<>();
        if (taskItems != null) {
            for (JsonNode item : taskItems) {
                items.add(toItem(item));
            }
        }
        return TaskListStructuredDto.builder()
                .tasklistGuid(guid)
                .tasklistName(name)
                .totalTasks(items.size())
                .openUrl(openUrl)
                .items(items)
                .build();
    }

    public static String exportPlainText(TaskListStructuredDto dto) {
        if (dto == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("【任务清单：").append(dto.getTasklistName() != null ? dto.getTasklistName() : "任务清单");
        sb.append("，共 ").append(dto.getTotalTasks()).append(" 项】\n\n");
        List<TaskListItemDto> items = dto.getItems();
        if (items == null || items.isEmpty()) {
            sb.append("（无任务）");
            return sb.toString().trim();
        }
        for (int i = 0; i < items.size(); i++) {
            TaskListItemDto t = items.get(i);
            sb.append(i + 1).append(". ");
            sb.append(t.getSummary() != null ? t.getSummary() : "（无标题）");
            sb.append(" [").append(statusLabel(t.getStatus())).append(']');
            if (t.getDueAt() != null && !t.getDueAt().isBlank()) {
                sb.append(" 截止 ").append(t.getDueAt());
            }
            if (t.getAssignees() != null && !t.getAssignees().isBlank()) {
                sb.append(" 负责人 ").append(t.getAssignees());
            }
            sb.append('\n');
        }
        return sb.toString().trim();
    }

    private static TaskListItemDto toItem(JsonNode item) {
        String completedAt = formatTimestamp(item.path("completed_at").asText(""));
        String status = completedAt.isEmpty() ? "in_progress" : "completed";
        return TaskListItemDto.builder()
                .taskGuid(item.path("guid").asText(null))
                .summary(item.path("summary").asText("").trim())
                .status(status)
                .dueAt(formatDue(item.path("due")))
                .completedAt(completedAt)
                .assignees(formatAssignees(item.path("members")))
                .build();
    }

    private static String formatDue(JsonNode due) {
        if (due == null || due.isMissingNode() || due.isNull()) {
            return "";
        }
        String ts = due.path("timestamp").asText("");
        if (ts.isBlank() || "0".equals(ts)) {
            return "";
        }
        String formatted = formatTimestamp(ts);
        if (due.path("is_all_day").asBoolean(false) && formatted.length() >= 10) {
            return formatted.substring(0, 10);
        }
        return formatted;
    }

    private static String formatAssignees(JsonNode members) {
        if (members == null || !members.isArray() || members.isEmpty()) {
            return "";
        }
        List<String> names = new ArrayList<>();
        for (JsonNode m : members) {
            String role = m.path("role").asText("").toLowerCase(Locale.ROOT);
            if (!role.isEmpty() && !"assignee".equals(role) && !"owner".equals(role)) {
                continue;
            }
            String id = m.path("id").asText("").trim();
            if (!id.isEmpty()) {
                names.add(id);
            }
        }
        if (names.isEmpty()) {
            for (JsonNode m : members) {
                String id = m.path("id").asText("").trim();
                if (!id.isEmpty()) {
                    names.add(id);
                }
            }
        }
        return String.join("、", names);
    }

    private static String formatTimestamp(String ms) {
        if (ms == null || ms.isBlank() || "0".equals(ms.trim())) {
            return "";
        }
        try {
            long v = Long.parseLong(ms.trim());
            if (v <= 0) {
                return "";
            }
            return Instant.ofEpochMilli(v).atZone(ZONE).format(DT);
        } catch (NumberFormatException e) {
            return ms.trim();
        }
    }

    private static String statusLabel(String status) {
        return "completed".equals(status) ? "已完成" : "进行中";
    }
}

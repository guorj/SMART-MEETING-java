package com.smartmeeting.admin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartmeeting.admin.api.dto.HostAgendaItemRowDto;
import com.smartmeeting.admin.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class HostAgendaJsonHelper {

    private final ObjectMapper objectMapper;

    public List<HostAgendaItemRowDto> parseItems(String hostAgendaJson) {
        if (hostAgendaJson == null || hostAgendaJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(hostAgendaJson);
            JsonNode items = root.has("items") ? root.get("items") : root;
            if (!items.isArray()) {
                throw new BusinessException("host_agenda 须为 {\"items\":[...]} 形态");
            }
            List<HostAgendaItemRowDto> rows = new ArrayList<>();
            int i = 0;
            for (JsonNode n : items) {
                String title = n.path("title").asText("").trim();
                if (title.isEmpty()) {
                    i++;
                    continue;
                }
                rows.add(HostAgendaItemRowDto.builder()
                        .index(i++)
                        .title(title)
                        .minutes(n.path("minutes").asInt(10))
                        .hasRollCallKeyword(title.contains("检点"))
                        .build());
            }
            return rows;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("host_agenda JSON 无效: " + e.getMessage());
        }
    }

    public String toJson(List<HostAgendaItemRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        try {
            ObjectNode root = objectMapper.createObjectNode();
            ArrayNode arr = root.putArray("items");
            for (HostAgendaItemRowDto row : rows) {
                if (row.getTitle() == null || row.getTitle().isBlank()) {
                    continue;
                }
                ObjectNode item = arr.addObject();
                item.put("title", row.getTitle().trim());
                item.put("minutes", row.getMinutes() != null && row.getMinutes() > 0 ? row.getMinutes() : 10);
            }
            if (arr.isEmpty()) {
                return null;
            }
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            throw new BusinessException("序列化 host_agenda 失败");
        }
    }

    public List<String> validate(String hostAgendaJson) {
        List<String> issues = new ArrayList<>();
        if (hostAgendaJson == null || hostAgendaJson.isBlank()) {
            issues.add("host_agenda 为空");
            return issues;
        }
        List<HostAgendaItemRowDto> items = parseItems(hostAgendaJson);
        if (items.isEmpty()) {
            issues.add("无有效会序项（title 不能为空）");
        }
        boolean anyRollCall = items.stream().anyMatch(HostAgendaItemRowDto::isHasRollCallKeyword);
        if (!anyRollCall) {
            issues.add("提示：无标题含「检点」的会序，主持不会自动进入检点");
        }
        return issues;
    }
}

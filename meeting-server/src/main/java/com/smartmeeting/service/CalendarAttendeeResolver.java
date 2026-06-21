package com.smartmeeting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.entity.Participant;
import com.smartmeeting.repository.ParticipantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 为会前日历步骤收集待邀请的飞书 user_id 列表。
 */
@Service
@RequiredArgsConstructor
public class CalendarAttendeeResolver {

    private final ParticipantMapper participantMapper;
    private final FeishuUserIdResolver feishuUserIdResolver;
    private final ObjectMapper objectMapper;

    public record ResolvedAttendees(List<String> feishuUserIds, List<String> skippedRawIds) {
    }

    public ResolvedAttendees resolve(Meeting meeting, JsonNode config) {
        if (meeting == null) {
            return new ResolvedAttendees(List.of(), List.of());
        }
        String source = config.path("attendeeSource").asText("participants_and_creator");
        boolean includeCreator = config.path("includeCreator").asBoolean(true);
        Set<String> rawIds = new LinkedHashSet<>();
        switch (source) {
            case "participants" -> addParticipants(meeting.getId(), rawIds);
            case "creator" -> {
                if (includeCreator && meeting.getCreatorId() != null) {
                    rawIds.add(meeting.getCreatorId());
                }
            }
            case "host_agenda_owners" -> addHostAgendaOwners(meeting.getHostAgenda(), rawIds);
            default -> {
                addParticipants(meeting.getId(), rawIds);
                if (includeCreator && meeting.getCreatorId() != null && !meeting.getCreatorId().isBlank()) {
                    rawIds.add(meeting.getCreatorId());
                }
            }
        }
        List<String> resolved = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (String raw : rawIds) {
            feishuUserIdResolver.resolve(raw).ifPresentOrElse(resolved::add, () -> skipped.add(raw));
        }
        return new ResolvedAttendees(resolved, skipped);
    }

    private void addParticipants(String meetingId, Set<String> rawIds) {
        if (meetingId == null || meetingId.isBlank()) {
            return;
        }
        List<Participant> rows = participantMapper.selectList(new LambdaQueryWrapper<Participant>()
                .eq(Participant::getMeetingId, meetingId));
        for (Participant p : rows) {
            feishuUserIdResolver.resolveParticipant(p.getUserId(), p.getName()).ifPresent(rawIds::add);
        }
    }

    private void addHostAgendaOwners(String hostAgendaJson, Set<String> rawIds) {
        if (hostAgendaJson == null || hostAgendaJson.isBlank()) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(hostAgendaJson);
            JsonNode items = root.path("items");
            if (!items.isArray()) {
                return;
            }
            for (JsonNode item : items) {
                JsonNode owners = item.path("owners");
                if (!owners.isArray()) {
                    continue;
                }
                for (JsonNode owner : owners) {
                    String uid = owner.asText("").trim();
                    if (!uid.isBlank()) {
                        rawIds.add(uid);
                    }
                }
            }
        } catch (Exception ignored) {
            // invalid host_agenda json
        }
    }
}

package com.smartmeeting.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartmeeting.config.MeetingVcProperties;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.pipeline.executor.PipelineExecutorSupport;
import com.smartmeeting.repository.MeetingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 将会议计划时间同步到飞书日历（创建/更新 event，写入 meeting.room_id）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingCalendarSyncService {

    private static final int DEFAULT_DURATION_MINUTES = 60;

    private final FeishuService feishuService;
    private final MeetingMapper meetingMapper;
    private final CalendarAttendeeResolver calendarAttendeeResolver;
    private final ObjectMapper objectMapper;
    private final PipelineExecutorSupport pipelineSupport;
    private final MeetingVcProperties vcProperties;

    public record SyncResult(boolean success, String action, String eventId, String message,
                             int invitedCount, String vcMeetingUrl) {
    }

    /**
     * 取消预约时删除飞书日历日程（meeting.room_id 即 event_id）。
     * 失败仅打日志，不阻断上层取消流程。
     */
    public SyncResult deleteScheduledCalendarEvent(Meeting meeting) {
        if (meeting == null) {
            return new SyncResult(false, "skip", "", "meeting_missing", 0, "");
        }
        String eventId = meeting.getRoomId();
        if (eventId == null || eventId.isBlank()) {
            return new SyncResult(true, "skip", "", "no_event_id", 0, "");
        }
        FeishuService.CalendarDeleteResult deleted = feishuService.deleteCalendarEvent(eventId);
        if (deleted.success()) {
            log.info("Meeting calendar deleted: meetingId={}, eventId={}", meeting.getId(), eventId);
            return new SyncResult(true, "deleted", eventId, "ok", 0, "");
        }
        log.warn("Meeting calendar delete failed: meetingId={}, eventId={}, err={}",
                meeting.getId(), eventId, deleted.message());
        return new SyncResult(false, "delete", eventId, deleted.message(), 0, "");
    }

    public SyncResult syncScheduledMeeting(Meeting meeting) {
        return syncScheduledMeeting(meeting, DEFAULT_DURATION_MINUTES, "", true, resolveVchatOptionsForSchedule());
    }

    public SyncResult syncScheduledMeeting(Meeting meeting, int durationMinutes, String roomHint, boolean upsert) {
        return syncScheduledMeeting(meeting, durationMinutes, roomHint, upsert, resolveVchatOptionsForSchedule());
    }

    /**
     * 预约会议路径的 vchat 选项：
     * {@code meeting.vc.recording-enabled=true} 时按配置覆盖 autoRecord；
     * 否则用 {@link CalendarVchatOptions#defaults()}（autoRecord=false）。
     */
    private CalendarVchatOptions resolveVchatOptionsForSchedule() {
        if (!vcProperties.isRecordingEnabled()) {
            return CalendarVchatOptions.defaults();
        }
        return new CalendarVchatOptions(
                true,
                CalendarVchatOptions.VC_TYPE_VC,
                "",
                "only_event_attendees",
                true,
                vcProperties.isAutoRecord(),
                true,
                5);
    }

    public SyncResult syncScheduledMeeting(Meeting meeting, int durationMinutes, String roomHint, boolean upsert,
                                           CalendarVchatOptions vchatOptions) {
        if (meeting == null) {
            return new SyncResult(false, "skip", "", "meeting_missing", 0, "");
        }
        if (meeting.getScheduledTime() == null) {
            return new SyncResult(false, "skip", "", "no_scheduled_time", 0, "");
        }

        int duration = Math.max(15, durationMinutes);
        LocalDateTime startAt = meeting.getScheduledTime();
        LocalDateTime endAt = startAt.plusMinutes(duration);
        String title = meeting.getTitle() == null || meeting.getTitle().isBlank()
                ? "会议邀约"
                : meeting.getTitle();
        CalendarVchatOptions vchat = vchatOptions != null ? vchatOptions : CalendarVchatOptions.defaults();
        JsonNode attendeeCfg = defaultAttendeeConfig();

        String existingEventId = meeting.getRoomId();
        boolean hasEvent = existingEventId != null && !existingEventId.isBlank();

        if (hasEvent && upsert) {
            SyncResult updated = syncCalendarEvent(meeting, startAt, endAt, title, roomHint, true, vchat, attendeeCfg);
            if (updated.success()) {
                return updated;
            }
            log.warn("Meeting calendar update failed, fallback to create: meetingId={}, eventId={}, err={}",
                    meeting.getId(), existingEventId, updated.message());
        }

        return createCalendarEvent(meeting, startAt, endAt, title, roomHint, vchat, attendeeCfg);
    }

    /**
     * 流水线 pre-calendar-create 步骤：解析步骤 config 并同步日历。
     */
    public SyncResult syncFromPipeline(Meeting meeting, JsonNode stepCfg) {
        if (meeting == null) {
            return new SyncResult(false, "skip", "", "meeting_missing", 0, "");
        }
        JsonNode cfg = stepCfg == null ? objectMapper.createObjectNode() : stepCfg;
        String calendarMode = pipelineSupport.text(cfg, "calendarMode", "upsert").trim().toLowerCase();
        String existingEventId = meeting.getRoomId();
        boolean hasEvent = existingEventId != null && !existingEventId.isBlank();

        if ("create".equals(calendarMode) && hasEvent) {
            return new SyncResult(true, "skip", existingEventId, "event_already_exists", 0, "");
        }
        if ("update".equals(calendarMode) && !hasEvent) {
            return new SyncResult(true, "skip", "", "no_event_id", 0, "");
        }

        LocalDateTime startAt = resolvePipelineStartAt(meeting, cfg);
        if (startAt == null) {
            return new SyncResult(false, "skip", "", "calendar-start-time-unresolved", 0, "");
        }

        int durationMinutes = Math.max(15, pipelineSupport.number(cfg, "durationMinutes", DEFAULT_DURATION_MINUTES));
        LocalDateTime endAt = startAt.plusMinutes(durationMinutes);
        String roomHint = pipelineSupport.text(cfg, "roomHint", "");
        CalendarVchatOptions vchat = CalendarVchatOptions.from(cfg.path("vchat"));
        JsonNode attendeeCfg = mergeAttendeeConfig(cfg);
        String title = meeting.getTitle() == null || meeting.getTitle().isBlank() ? "会议邀约" : meeting.getTitle();
        boolean upsert = "upsert".equals(calendarMode);
        boolean updateOnly = "update".equals(calendarMode);

        if (updateOnly && hasEvent) {
            SyncResult updated = syncCalendarEvent(meeting, startAt, endAt, title, roomHint, true, vchat, attendeeCfg);
            if (!updated.success()) {
                return new SyncResult(false, "update", existingEventId, updated.message(), 0, "");
            }
            return updated;
        }

        if (upsert && hasEvent) {
            SyncResult updated = syncCalendarEvent(meeting, startAt, endAt, title, roomHint, true, vchat, attendeeCfg);
            if (updated.success()) {
                return updated;
            }
            log.warn("pipeline calendar upsert update failed, fallback create: meetingId={}, err={}",
                    meeting.getId(), updated.message());
        }

        return createCalendarEvent(meeting, startAt, endAt, title, roomHint, vchat, attendeeCfg);
    }

    private SyncResult syncCalendarEvent(Meeting meeting, LocalDateTime startAt, LocalDateTime endAt,
                                         String title, String roomHint, boolean tryUpdate,
                                         CalendarVchatOptions vchat, JsonNode attendeeCfg) {
        String ownerUserId = meeting.getCreatorId();
        String existingEventId = meeting.getRoomId();
        boolean hasEvent = existingEventId != null && !existingEventId.isBlank();

        if (tryUpdate && hasEvent) {
            FeishuService.CalendarUpdateResult update = feishuService.updateCalendarEvent(
                    existingEventId, startAt, endAt, title, roomHint, true, vchat, ownerUserId);
            if (update.success()) {
                int invitedCount = syncEventAttendees(meeting, attendeeCfg, update.eventId());
                log.info("Meeting calendar updated: meetingId={}, eventId={}, invitedCount={}",
                        meeting.getId(), update.eventId(), invitedCount);
                return new SyncResult(true, "updated", update.eventId(), "ok", invitedCount, "");
            }
            log.warn("Meeting calendar update failed: meetingId={}, eventId={}, err={}",
                    meeting.getId(), existingEventId, update.message());
            return new SyncResult(false, "update", existingEventId, update.message(), 0, "");
        }
        return createCalendarEvent(meeting, startAt, endAt, title, roomHint, vchat, attendeeCfg);
    }

    private int syncEventAttendees(Meeting meeting, JsonNode attendeeCfg, String eventId) {
        JsonNode cfg = attendeeCfg == null ? defaultAttendeeConfig() : attendeeCfg;
        CalendarAttendeeResolver.ResolvedAttendees attendees = calendarAttendeeResolver.resolve(meeting, cfg);
        boolean hasUsers = attendees.feishuUserIds() != null && !attendees.feishuUserIds().isEmpty();
        boolean hasChat = meeting.getChatId() != null && !meeting.getChatId().isBlank();
        if (!hasUsers && !hasChat) {
            return 0;
        }
        FeishuService.CalendarAttendeeAddResult addResult = feishuService.addCalendarEventAttendees(
                "primary", eventId, attendees.feishuUserIds(), meeting.getChatId(), true);
        if (!addResult.success()) {
            log.warn("Meeting calendar attendee sync failed: meetingId={}, eventId={}, err={}",
                    meeting.getId(), eventId, addResult.message());
            return 0;
        }
        return addResult.invitedCount();
    }

    private SyncResult createCalendarEvent(Meeting meeting, LocalDateTime startAt, LocalDateTime endAt,
                                           String title, String roomHint, CalendarVchatOptions vchat,
                                           JsonNode attendeeCfg) {
        CalendarAttendeeResolver.ResolvedAttendees attendees = calendarAttendeeResolver.resolve(meeting, attendeeCfg);
        FeishuService.CalendarCreateResult create = feishuService.createCalendarEvent(
                title,
                startAt,
                endAt,
                roomHint,
                meeting.getChatId(),
                attendees.feishuUserIds(),
                vchat,
                meeting.getCreatorId());
        if (!create.success() || create.eventId() == null || create.eventId().isBlank()) {
            log.warn("Meeting calendar create failed: meetingId={}, err={}", meeting.getId(), create.message());
            return new SyncResult(false, "create", "", create.message(), 0, "");
        }
        meeting.setRoomId(create.eventId());
        if (create.vcMeetingUrl() != null && !create.vcMeetingUrl().isBlank()) {
            meeting.setVcMeetingUrl(create.vcMeetingUrl());
        }
        meetingMapper.updateById(meeting);
        log.info("Meeting calendar created: meetingId={}, eventId={}, invitedCount={}, vcMeetingUrl={}",
                meeting.getId(), create.eventId(), create.invitedCount(), create.vcMeetingUrl());
        return new SyncResult(true, "created", create.eventId(), "ok", create.invitedCount(), create.vcMeetingUrl());
    }

    private LocalDateTime resolvePipelineStartAt(Meeting meeting, JsonNode cfg) {
        String source = pipelineSupport.text(cfg, "startTimeSource", "").trim().toLowerCase();
        if (source.isBlank()) {
            if (meeting.getScheduledTime() != null) {
                return meeting.getScheduledTime();
            }
            int plus = Math.max(1, pipelineSupport.number(cfg, "triggerPlusMinutes", 5));
            return LocalDateTime.now().plusMinutes(plus);
        }
        return switch (source) {
            case "scheduled_time" -> {
                if (meeting.getScheduledTime() != null) {
                    yield meeting.getScheduledTime();
                }
                int plus = Math.max(1, pipelineSupport.number(cfg, "triggerPlusMinutes", 5));
                yield LocalDateTime.now().plusMinutes(plus);
            }
            case "trigger_plus_minutes" -> {
                int plus = Math.max(1, pipelineSupport.number(cfg, "triggerPlusMinutes", 5));
                yield LocalDateTime.now().plusMinutes(plus);
            }
            case "actual_start_time" -> {
                if (meeting.getActualStartTime() != null) {
                    yield meeting.getActualStartTime();
                }
                int plus = Math.max(1, pipelineSupport.number(cfg, "triggerPlusMinutes", 5));
                yield LocalDateTime.now().plusMinutes(plus);
            }
            case "config_start_at" -> parseConfigStartAt(cfg);
            default -> meeting.getScheduledTime() != null
                    ? meeting.getScheduledTime()
                    : LocalDateTime.now().plusMinutes(Math.max(1, pipelineSupport.number(cfg, "triggerPlusMinutes", 5)));
        };
    }

    private LocalDateTime parseConfigStartAt(JsonNode cfg) {
        String raw = pipelineSupport.text(cfg, "startAt", "").trim();
        if (raw.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw.replace(' ', 'T'));
        } catch (Exception ignored) {
            return null;
        }
    }

    private JsonNode defaultAttendeeConfig() {
        ObjectNode cfg = objectMapper.createObjectNode();
        cfg.put("attendeeSource", "participants_and_creator");
        cfg.put("includeCreator", true);
        return cfg;
    }

    private JsonNode mergeAttendeeConfig(JsonNode stepCfg) {
        ObjectNode merged = defaultAttendeeConfig().deepCopy();
        if (stepCfg == null || !stepCfg.isObject()) {
            return merged;
        }
        if (stepCfg.hasNonNull("attendeeSource")) {
            merged.put("attendeeSource", stepCfg.path("attendeeSource").asText());
        }
        if (stepCfg.has("includeCreator")) {
            merged.put("includeCreator", stepCfg.path("includeCreator").asBoolean(true));
        }
        return merged;
    }
}

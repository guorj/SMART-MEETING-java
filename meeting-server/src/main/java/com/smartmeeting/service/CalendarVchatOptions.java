package com.smartmeeting.service;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 飞书日历日程绑定的视频会议选项（vchat）。
 */
public record CalendarVchatOptions(
        boolean enabled,
        String vcType,
        String meetingUrl,
        String joinMeetingPermission,
        boolean openLobby,
        boolean autoRecord,
        boolean allowAttendeesStart,
        int reminderMinutes) {

    public static final String VC_TYPE_VC = "vc";
    public static final String VC_TYPE_NO_MEETING = "no_meeting";
    public static final String VC_TYPE_THIRD_PARTY = "third_party";

    public static CalendarVchatOptions defaults() {
        return new CalendarVchatOptions(
                true,
                VC_TYPE_VC,
                "",
                "only_event_attendees",
                true,
                false,
                true,
                5);
    }

    public static CalendarVchatOptions from(JsonNode cfg) {
        if (cfg == null || cfg.isMissingNode() || cfg.isNull()) {
            return defaults();
        }
        boolean enabled = !cfg.has("enabled") || cfg.path("enabled").asBoolean(true);
        String vcType = text(cfg, "vcType", enabled ? VC_TYPE_VC : VC_TYPE_NO_MEETING);
        if (!enabled) {
            vcType = VC_TYPE_NO_MEETING;
        }
        return new CalendarVchatOptions(
                enabled && !VC_TYPE_NO_MEETING.equalsIgnoreCase(vcType),
                vcType.trim().toLowerCase(),
                text(cfg, "meetingUrl", ""),
                text(cfg, "joinMeetingPermission", "only_event_attendees"),
                cfg.path("openLobby").asBoolean(true),
                cfg.path("autoRecord").asBoolean(false),
                cfg.path("allowAttendeesStart").asBoolean(true),
                Math.max(0, cfg.path("reminderMinutes").asInt(5)));
    }

    private static String text(JsonNode cfg, String field, String defaultValue) {
        if (cfg == null || !cfg.has(field)) {
            return defaultValue;
        }
        String v = cfg.path(field).asText("").trim();
        return v.isBlank() ? defaultValue : v;
    }
}

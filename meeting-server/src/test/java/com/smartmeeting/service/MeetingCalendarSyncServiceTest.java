package com.smartmeeting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.pipeline.executor.PipelineExecutorSupport;
import com.smartmeeting.repository.MeetingMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeetingCalendarSyncServiceTest {

    @Mock
    private FeishuService feishuService;
    @Mock
    private MeetingMapper meetingMapper;
    @Mock
    private CalendarAttendeeResolver calendarAttendeeResolver;

    private MeetingCalendarSyncService service;

    @BeforeEach
    void setUp() {
        service = new MeetingCalendarSyncService(
                feishuService,
                meetingMapper,
                calendarAttendeeResolver,
                new ObjectMapper(),
                new PipelineExecutorSupport(new ObjectMapper()));
    }

    @Test
    void syncScheduledMeeting_skipsWhenNoScheduledTime() {
        Meeting meeting = new Meeting();
        meeting.setId("m1");

        MeetingCalendarSyncService.SyncResult result = service.syncScheduledMeeting(meeting);

        assertFalse(result.success());
        assertEquals("no_scheduled_time", result.message());
        verify(feishuService, never()).createCalendarEvent(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void syncScheduledMeeting_createsEventWithDefaultVchat() {
        Meeting meeting = meetingWithSchedule("m2", null);
        when(calendarAttendeeResolver.resolve(eq(meeting), any()))
                .thenReturn(new CalendarAttendeeResolver.ResolvedAttendees(List.of("u1"), List.of()));
        when(feishuService.createCalendarEvent(any(), any(), any(), any(), any(), any(), any(), eq("creator1")))
                .thenReturn(new FeishuService.CalendarCreateResult(true, "evt-1", "primary", "ok", 1, 0, "https://vc.feishu.cn/j/1"));

        MeetingCalendarSyncService.SyncResult result = service.syncScheduledMeeting(meeting);

        assertTrue(result.success());
        assertEquals("created", result.action());
        assertEquals("https://vc.feishu.cn/j/1", result.vcMeetingUrl());
        ArgumentCaptor<CalendarVchatOptions> vchatCaptor = ArgumentCaptor.forClass(CalendarVchatOptions.class);
        verify(feishuService).createCalendarEvent(any(), any(), any(), any(), any(), any(),
                vchatCaptor.capture(), eq("creator1"));
        assertTrue(vchatCaptor.getValue().enabled());
        assertEquals(CalendarVchatOptions.VC_TYPE_VC, vchatCaptor.getValue().vcType());
    }

    @Test
    void syncScheduledMeeting_updatesWhenRoomIdExists() {
        Meeting meeting = meetingWithSchedule("m3", "evt-old");
        when(feishuService.updateCalendarEvent(eq("evt-old"), any(), any(), any(), any(), eq(true), any(), eq("creator1")))
                .thenReturn(new FeishuService.CalendarUpdateResult(true, "evt-old", "ok"));
        when(calendarAttendeeResolver.resolve(eq(meeting), any()))
                .thenReturn(new CalendarAttendeeResolver.ResolvedAttendees(List.of("u1"), List.of()));
        when(feishuService.addCalendarEventAttendees(eq("primary"), eq("evt-old"), any(), isNull(), eq(true)))
                .thenReturn(new FeishuService.CalendarAttendeeAddResult(true, 1, "ok"));

        MeetingCalendarSyncService.SyncResult result = service.syncScheduledMeeting(meeting);

        assertTrue(result.success());
        assertEquals("updated", result.action());
        assertEquals(1, result.invitedCount());
        verify(feishuService).addCalendarEventAttendees(eq("primary"), eq("evt-old"), eq(List.of("u1")), isNull(), eq(true));
        verify(feishuService, never()).createCalendarEvent(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void syncScheduledMeeting_fallsBackToCreateWhenUpdateFails() {
        Meeting meeting = meetingWithSchedule("m4", "evt-stale");
        when(feishuService.updateCalendarEvent(eq("evt-stale"), any(), any(), any(), any(), eq(true), any(), eq("creator1")))
                .thenReturn(new FeishuService.CalendarUpdateResult(false, "evt-stale", "not_found"));
        when(calendarAttendeeResolver.resolve(eq(meeting), any()))
                .thenReturn(new CalendarAttendeeResolver.ResolvedAttendees(List.of(), List.of()));
        when(feishuService.createCalendarEvent(any(), any(), any(), any(), any(), any(), any(), eq("creator1")))
                .thenReturn(new FeishuService.CalendarCreateResult(true, "evt-new", "primary", "ok", 0, 0, ""));

        MeetingCalendarSyncService.SyncResult result = service.syncScheduledMeeting(meeting);

        assertTrue(result.success());
        assertEquals("created", result.action());
        assertEquals("evt-new", result.eventId());
    }

    @Test
    void syncFromPipeline_fallsBackWhenScheduledTimeMissing() {
        Meeting meeting = new Meeting();
        meeting.setId("m5");
        meeting.setTitle("即时会议");
        meeting.setCreatorId("creator1");

        ObjectNode cfg = new ObjectMapper().createObjectNode();
        cfg.put("startTimeSource", "scheduled_time");
        cfg.put("triggerPlusMinutes", 10);
        cfg.put("calendarMode", "create");

        when(calendarAttendeeResolver.resolve(eq(meeting), any()))
                .thenReturn(new CalendarAttendeeResolver.ResolvedAttendees(List.of("u1"), List.of()));
        when(feishuService.createCalendarEvent(any(), any(), any(), any(), any(), any(), any(), eq("creator1")))
                .thenReturn(new FeishuService.CalendarCreateResult(true, "evt-new", "primary", "ok", 1, 0, ""));

        MeetingCalendarSyncService.SyncResult result = service.syncFromPipeline(meeting, cfg);

        assertTrue(result.success());
        assertEquals("created", result.action());
        verify(feishuService).createCalendarEvent(any(), any(), any(), any(), any(), eq(List.of("u1")), any(), eq("creator1"));
    }

    @Test
    void syncFromPipeline_upsertUpdateSyncsAttendees() {
        Meeting meeting = meetingWithSchedule("m6", "evt-old");
        ObjectNode cfg = new ObjectMapper().createObjectNode();
        cfg.put("calendarMode", "upsert");
        cfg.put("startTimeSource", "scheduled_time");

        when(feishuService.updateCalendarEvent(eq("evt-old"), any(), any(), any(), any(), eq(true), any(), eq("creator1")))
                .thenReturn(new FeishuService.CalendarUpdateResult(true, "evt-old", "ok"));
        when(calendarAttendeeResolver.resolve(eq(meeting), any()))
                .thenReturn(new CalendarAttendeeResolver.ResolvedAttendees(List.of("u1", "u2"), List.of()));
        when(feishuService.addCalendarEventAttendees(eq("primary"), eq("evt-old"), any(), isNull(), eq(true)))
                .thenReturn(new FeishuService.CalendarAttendeeAddResult(true, 2, "ok"));

        MeetingCalendarSyncService.SyncResult result = service.syncFromPipeline(meeting, cfg);

        assertTrue(result.success());
        assertEquals("updated", result.action());
        assertEquals(2, result.invitedCount());
        verify(feishuService, never()).createCalendarEvent(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void syncFromPipeline_createModeSkipsWhenEventExists() {
        Meeting meeting = meetingWithSchedule("m7", "evt-existing");
        ObjectNode cfg = new ObjectMapper().createObjectNode();
        cfg.put("calendarMode", "create");

        MeetingCalendarSyncService.SyncResult result = service.syncFromPipeline(meeting, cfg);

        assertTrue(result.success());
        assertEquals("skip", result.action());
        assertEquals("event_already_exists", result.message());
        verify(feishuService, never()).createCalendarEvent(any(), any(), any(), any(), any(), any(), any(), any());
        verify(feishuService, never()).updateCalendarEvent(any(), any(), any(), any(), any(), eq(true), any(), any());
    }

    private static Meeting meetingWithSchedule(String id, String roomId) {
        Meeting meeting = new Meeting();
        meeting.setId(id);
        meeting.setTitle("测试会议");
        meeting.setScheduledTime(LocalDateTime.now().plusDays(1));
        meeting.setRoomId(roomId);
        meeting.setCreatorId("creator1");
        return meeting;
    }
}

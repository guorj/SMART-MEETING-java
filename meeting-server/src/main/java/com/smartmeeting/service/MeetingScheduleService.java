package com.smartmeeting.service;

import com.smartmeeting.api.dto.MeetingScheduleRequest;
import com.smartmeeting.api.dto.MeetingScheduleResponse;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.enums.MeetingStatus;
import com.smartmeeting.exception.BusinessException;
import com.smartmeeting.repository.MeetingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingScheduleService {

    private static final Set<String> RESCHEDULABLE = Set.of(
            MeetingStatus.ISSUE_COLLECTING.name(),
            MeetingStatus.INVITED.name());

    private final MeetingMapper meetingMapper;
    private final FeishuService feishuService;
    private final MeetingCalendarSyncService meetingCalendarSyncService;

    @Transactional
    public MeetingScheduleResponse reschedule(String meetingId, MeetingScheduleRequest request) {
        if (request == null || request.getScheduledTime() == null) {
            throw new BusinessException(400, "scheduledTime 不能为空");
        }
        Meeting meeting = meetingMapper.selectById(meetingId);
        if (meeting == null) {
            throw new BusinessException(404, "会议不存在: " + meetingId);
        }
        if (meeting.getStatus() == null || !RESCHEDULABLE.contains(meeting.getStatus())) {
            throw new BusinessException(400, "当前状态不允许改期: " + meeting.getStatus());
        }
        LocalDateTime newTime = request.getScheduledTime();
        if (!newTime.isAfter(LocalDateTime.now())) {
            throw new BusinessException(400, "计划开始时间须晚于当前时刻");
        }

        meeting.setScheduledTime(newTime);
        meetingMapper.updateById(meeting);

        boolean syncCalendar = request.getSyncCalendar() == null || Boolean.TRUE.equals(request.getSyncCalendar());
        boolean notifyChat = request.getNotifyChat() == null || Boolean.TRUE.equals(request.getNotifyChat());
        int durationMinutes = request.getDurationMinutes() != null && request.getDurationMinutes() > 0
                ? request.getDurationMinutes() : 60;

        boolean calendarSynced = false;
        String calendarReason = "sync_disabled";
        String eventId = meeting.getRoomId();

        if (syncCalendar) {
            MeetingCalendarSyncService.SyncResult cal = meetingCalendarSyncService.syncScheduledMeeting(
                    meeting, durationMinutes, "", true);
            calendarSynced = cal.success();
            calendarReason = cal.success() ? cal.action() : cal.message();
            if (cal.eventId() != null && !cal.eventId().isBlank()) {
                eventId = cal.eventId();
            } else {
                Meeting refreshed = meetingMapper.selectById(meetingId);
                if (refreshed != null && refreshed.getRoomId() != null && !refreshed.getRoomId().isBlank()) {
                    eventId = refreshed.getRoomId();
                }
            }
        }

        if (notifyChat && meeting.getChatId() != null && !meeting.getChatId().isBlank()) {
            String timeHint = newTime.toString().replace('T', ' ').substring(0, 16);
            String notify = "会议改期：" + (meeting.getTitle() != null ? meeting.getTitle() : meetingId)
                    + "\n新时间：" + timeHint;
            feishuService.sendMessage(meeting.getChatId(), notify);
        }

        log.info("Meeting rescheduled: meetingId={}, scheduledTime={}, calendarSynced={}",
                meetingId, newTime, calendarSynced);

        return MeetingScheduleResponse.builder()
                .meetingId(meetingId)
                .scheduledTime(newTime)
                .calendarSynced(calendarSynced)
                .calendarReason(calendarReason)
                .eventId(eventId)
                .build();
    }
}

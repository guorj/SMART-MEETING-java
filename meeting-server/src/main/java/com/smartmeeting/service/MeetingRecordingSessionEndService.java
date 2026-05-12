package com.smartmeeting.service;

import com.smartmeeting.api.dto.MeetingResponse;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.enums.MeetingStatus;
import com.smartmeeting.exception.BusinessException;
import com.smartmeeting.repository.MeetingMapper;
import com.smartmeeting.service.host.MeetingHostMediaTeardownService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 录音页「结束会议」：与飞书指令共用纪要触发，但优先走录音停录逻辑，避免 stopRecording 与 endMeeting 重复发事件。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingRecordingSessionEndService {

    private final MeetingMapper meetingMapper;
    private final RecordingService recordingService;
    private final MeetingService meetingService;
    private final FeishuService feishuService;
    private final FeishuCardBuilder cardBuilder;
    private final MeetingHostMediaTeardownService meetingHostMediaTeardownService;

    public MeetingResponse endFromRecordingPage(String meetingId) {
        meetingHostMediaTeardownService.beforeRecordingSessionEnd(meetingId);
        Meeting meeting = meetingMapper.selectById(meetingId);
        if (meeting == null) {
            throw new BusinessException(404, "会议不存在: " + meetingId);
        }
        String status = meeting.getStatus();

        if (MeetingStatus.PROCESSING.name().equals(status)
                || MeetingStatus.COMPLETED.name().equals(status)
                || MeetingStatus.TODO_TRACKING.name().equals(status)) {
            return meetingService.getMeeting(meetingId);
        }

        sendProcessingCardIfPossible(meeting, status);

        if (MeetingStatus.RECORDING.name().equals(status) || MeetingStatus.PAUSED.name().equals(status)) {
            if (recordingService.getRecordingState(meetingId) != null) {
                recordingService.stopRecording(meetingId);
                return meetingService.getMeeting(meetingId);
            }
            log.warn("会议状态为 {} 但内存中无录音会话，回退为 endMeeting: {}", status, meetingId);
            return meetingService.endMeeting(meetingId);
        }

        if (MeetingStatus.STARTED.name().equals(status) || MeetingStatus.REVIEWING.name().equals(status)) {
            return meetingService.endMeeting(meetingId);
        }

        throw new BusinessException(400, "会议状态不允许结束: " + status);
    }

    private void sendProcessingCardIfPossible(Meeting meeting, String status) {
        String chatId = meeting.getChatId();
        if (chatId == null || chatId.isBlank()) {
            return;
        }
        if (!MeetingStatus.RECORDING.name().equals(status)
                && !MeetingStatus.PAUSED.name().equals(status)
                && !MeetingStatus.STARTED.name().equals(status)
                && !MeetingStatus.REVIEWING.name().equals(status)) {
            return;
        }
        try {
            String card = cardBuilder.buildProcessingCard(meeting.getTitle());
            feishuService.sendInteractiveCard(chatId, card);
        } catch (Exception e) {
            log.warn("发送飞书「纪要生成中」卡片失败，继续结束会议: {}", e.getMessage());
        }
    }
}

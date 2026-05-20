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
 * 录音页「结束会议」编排服务。
 *
 * <p>与飞书「结束会议」指令共用纪要触发链路，但优先调用 {@link RecordingService#stopRecording}，
 * 避免与 {@link MeetingService#endMeeting} 重复投递纪要生成事件；无内存录音会话时回退 {@code endMeeting}。
 *
 * <p>主要协作：{@link MeetingHostMediaTeardownService}、{@link RecordingService}、
 * {@link MeetingService}、{@link FeishuService}、{@link FeishuCardBuilder}。
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

    /**
     * 从录音页结束会议：先拆媒体资源，再按状态停录或结束会议。
     *
     * @param meetingId 会议 ID
     * @return 结束后的会议视图
     * @throws BusinessException 会议不存在（404）或当前状态不允许结束（400）
     */
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

    /**
     * 在可结束的状态下向会议群发送「纪要生成中」飞书卡片（失败仅打日志）。
     *
     * @param meeting 会议实体
     * @param status  当前会议状态
     */
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

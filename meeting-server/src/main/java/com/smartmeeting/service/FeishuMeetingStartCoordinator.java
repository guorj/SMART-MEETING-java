package com.smartmeeting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmeeting.api.dto.MeetingCreateRequest;
import com.smartmeeting.api.dto.MeetingResponse;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.enums.MeetingStatus;
import com.smartmeeting.exception.BusinessException;
import com.smartmeeting.repository.MeetingMapper;
import com.smartmeeting.session.FeishuStartMeetingPendingStore;
import com.smartmeeting.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 创建会议、启动会议并向飞书会话推送「会议已开始」通知卡片（无「开始录音」按钮；发起人在 Web 录音页拾音）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeishuMeetingStartCoordinator {

    private final FeishuStartMeetingPendingStore startMeetingPendingStore;
    private final MeetingMapper meetingMapper;
    private final MeetingService meetingService;
    private final FeishuService feishuService;
    private final JwtUtil jwtUtil;
    private final FeishuCardBuilder cardBuilder;

    @Value("${meeting.base-url:http://localhost:8765}")
    private String baseUrl;

    public MeetingResponse createMeetingStartAndNotifyFeishu(String openId, String chatId, MeetingCreateRequest request) {
        startMeetingPendingStore.clear(openId, chatId);
        if (openId == null || openId.isBlank()) {
            throw new BusinessException(400, "无法识别您的飞书账号，请稍后重试");
        }
        LambdaQueryWrapper<Meeting> activeQuery = new LambdaQueryWrapper<>();
        activeQuery.eq(Meeting::getCreatorId, openId);
        activeQuery.in(Meeting::getStatus,
                MeetingStatus.ISSUE_COLLECTING.name(),
                MeetingStatus.STARTED.name(),
                MeetingStatus.RECORDING.name());
        Meeting active = meetingMapper.selectOne(activeQuery);
        if (active != null) {
            throw new BusinessException(400,
                    "您有一场正在进行的会议「" + active.getTitle() + "」，请先结束再创建新会议");
        }

        request.setCreatorId(openId);
        request.setChatId(chatId);

        MeetingResponse meeting = meetingService.createMeeting(request);
        String meetingId = meeting.getId();
        String displayTitle = meeting.getTitle();

        meetingService.startMeeting(meetingId);

        String recordingToken = jwtUtil.generateToken(meetingId, Map.of("type", "recording", "meetingId", meetingId));
        String recordingUrl = baseUrl + "/rec/" + meetingId + "?token=" + recordingToken;

        Meeting entity = meetingMapper.selectById(meetingId);
        entity.setRecordingToken(recordingToken);
        entity.setRecordingUrl(recordingUrl);
        meetingMapper.updateById(entity);

        String card = cardBuilder.buildMeetingStartedNotifyCard(meetingId, displayTitle, recordingUrl);
        feishuService.sendInteractiveCard(chatId, card);

        log.info("会议已创建: meetingId={}, title={}, recordingUrl={}", meetingId, displayTitle, recordingUrl);
        return meetingService.getMeeting(meetingId);
    }
}

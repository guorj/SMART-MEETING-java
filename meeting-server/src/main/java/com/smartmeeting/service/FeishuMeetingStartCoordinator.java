package com.smartmeeting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmeeting.api.dto.MeetingCreateRequest;
import com.smartmeeting.api.dto.MeetingResponse;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.enums.AttendanceMode;
import com.smartmeeting.enums.MeetingStatus;
import com.smartmeeting.exception.BusinessException;
import com.smartmeeting.repository.MeetingMapper;
import com.smartmeeting.session.FeishuStartMeetingPendingStore;
import com.smartmeeting.util.JwtUtil;
import com.smartmeeting.util.MeetingWebPageUrls;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 飞书侧「创建并启动会议」协调器：串联会议创建、启动、录音页 Token 生成与飞书通知推送。
 * <p>
 * 主要协作组件：{@link MeetingService}、{@link FeishuService}、{@link FeishuCardBuilder}、
 * {@link ParticipantLinkService}、{@link JwtUtil}、{@link FeishuStartMeetingPendingStore}。
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
    private final MeetingWebPageUrls meetingWebPageUrls;
    private final ParticipantLinkService participantLinkService;

    /**
     * 创建会议、立即启动、生成录音页链接并向群聊推送「会议已开始」卡片，同时向线上参会人单聊推送个人入会链接。
     *
     * @param openId  发起人飞书 open_id
     * @param chatId  会议群 chat_id
     * @param request 会议创建请求
     * @return 创建并启动后的完整会议响应
     * @throws BusinessException openId 为空（400）或已有进行中的会议（400）
     */
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

        String recordingToken = jwtUtil.generateOperatorMeetingToken(meetingId, JwtUtil.TYPE_RECORDING);
        String recordingUrl = meetingWebPageUrls.recordingPageUrl(meetingId, recordingToken);

        Meeting entity = meetingMapper.selectById(meetingId);
        entity.setRecordingToken(recordingToken);
        entity.setRecordingUrl(recordingUrl);
        meetingMapper.updateById(entity);

        String card = cardBuilder.buildMeetingStartedNotifyCard(meetingId, displayTitle, recordingUrl);
        feishuService.sendInteractiveCard(chatId, card);
        pushOnlineParticipantJoinLinks(meetingId, displayTitle);

        log.info("会议已创建: meetingId={}, title={}, recordingUrl={}", meetingId, displayTitle, recordingUrl);
        return meetingService.getMeeting(meetingId);
    }

    /**
     * 向每位线上参会人单聊推送个人入会链接（含 open_id 的参会人）。
     *
     * @param meetingId    会议 ID
     * @param meetingTitle 会议主题（用于卡片展示）
     */
    private void pushOnlineParticipantJoinLinks(String meetingId, String meetingTitle) {
        List<MeetingResponse.ParticipantDTO> links = participantLinkService.buildParticipantLinks(meetingId);
        for (MeetingResponse.ParticipantDTO p : links) {
            if (!AttendanceMode.ONLINE.name().equalsIgnoreCase(
                    p.getAttendanceMode() != null ? p.getAttendanceMode() : "")) {
                continue;
            }
            if (p.getJoinUrl() == null || p.getJoinUrl().isBlank()) {
                continue;
            }
            String openId = p.getUserId();
            if (openId == null || openId.isBlank()) {
                log.warn("线上参会人缺少 userId，无法单聊推送入会链接: meetingId={}, name={}",
                        meetingId, p.getName());
                continue;
            }
            String personalCard = cardBuilder.buildPersonalOnlineJoinCard(
                    meetingTitle, p.getName(), p.getJoinUrl());
            boolean sent = feishuService.sendInteractiveCardToOpenId(openId, personalCard);
            if (sent) {
                log.info("已推送个人入会链接: meetingId={}, openId={}, name={}",
                        meetingId, openId, p.getName());
            }
        }
    }
}

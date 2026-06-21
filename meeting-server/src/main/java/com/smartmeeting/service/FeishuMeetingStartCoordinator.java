package com.smartmeeting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 飞书侧「创建会议」协调器：串联会议创建、录音页 Token 生成与飞书通知推送。
 * <p>
 * 快速开始仅创建草稿（{@link MeetingStatus#ISSUE_COLLECTING}），每用户每模板最多保留 1 场草稿（模板 {@value com.smartmeeting.service.PresetDraftPolicy#UNLIMITED_DRAFT_PRESET_CODE} 除外）；
 * DB 状态变为 {@link MeetingStatus#STARTED} 须在主持页 {@code POST /api/v1/host/meetings/{id}/start} 后触发。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeishuMeetingStartCoordinator {

    private static final Set<String> NOT_STARTED = Set.of(
            MeetingStatus.ISSUE_COLLECTING.name(),
            MeetingStatus.INVITED.name());

    private final FeishuStartMeetingPendingStore startMeetingPendingStore;
    private final MeetingMapper meetingMapper;
    private final MeetingService meetingService;
    private final FeishuService feishuService;
    private final JwtUtil jwtUtil;
    private final FeishuCardBuilder cardBuilder;
    private final MeetingWebPageUrls meetingWebPageUrls;
    private final ParticipantLinkService participantLinkService;
    private final MeetingPreStageService meetingPreStageService;
    private final PresetScheduleTimeResolver presetScheduleTimeResolver;

    /**
     * 创建会议（或复用已有草稿/进行中会议）、生成录音页链接并推送飞书卡片。
     *
     * @param openId  发起人飞书 open_id
     * @param chatId  会议群 chat_id
     * @param request 会议创建请求
     * @return 会议响应（含 recordingUrl）
     * @throws BusinessException openId 为空（400）
     */
    public MeetingResponse createMeetingStartAndNotifyFeishu(String openId, String chatId, MeetingCreateRequest request) {
        startMeetingPendingStore.clear(openId, chatId);
        if (openId == null || openId.isBlank()) {
            throw new BusinessException(400, "无法识别您的飞书账号，请稍后重试");
        }

        Meeting active = findActiveMeeting(openId);
        if (active != null) {
            return returnExistingMeeting(openId, chatId, active, true);
        }

        Integer presetCode = request.getPresetTypeCode();
        if (PresetDraftPolicy.shouldReuseSingleDraft(presetCode)) {
            Meeting draft = findDraftByPreset(openId, presetCode);
            if (draft != null) {
                return returnExistingMeeting(openId, chatId, draft, false);
            }
        }

        request.setCreatorId(openId);
        request.setChatId(chatId);
        if (request.getScheduledTime() != null) {
            log.warn("Ignoring scheduledTime on instant-start path: openId={}, scheduledTime={}",
                    openId, request.getScheduledTime());
            request.setScheduledTime(null);
        }

        MeetingResponse meeting = meetingService.createMeeting(request);
        String meetingId = meeting.getId();
        String displayTitle = meeting.getTitle();

        applyPresetScheduledTimeIfNeeded(meetingId, meeting.getPresetTypeCode());
        meetingPreStageService.runPreStage(meetingId, meeting.getPresetTypeCode());

        String recordingToken = ensureRecordingToken(meetingId, openId);
        String recordingUrl = meetingWebPageUrls.recordingPageUrl(meetingId, recordingToken);
        meetingMapper.update(null, new LambdaUpdateWrapper<Meeting>()
                .eq(Meeting::getId, meetingId)
                .set(Meeting::getRecordingToken, recordingToken));

        sendCreatedNotifyCard(openId, chatId, meetingId, displayTitle, recordingUrl);

        log.info("会议草稿已创建: meetingId={}, title={}, recordingUrl={}", meetingId, displayTitle, recordingUrl);
        return meetingService.getMeeting(meetingId);
    }

    /**
     * 查询用户在某模板下的最新未开始草稿（单草稿模板用于复用；模板 99 建会路径不复用）。
     */
    public Meeting findDraftByPreset(String creatorId, int presetTypeCode) {
        if (creatorId == null || creatorId.isBlank() || presetTypeCode <= 0) {
            return null;
        }
        return meetingMapper.selectOne(new LambdaQueryWrapper<Meeting>()
                .eq(Meeting::getCreatorId, creatorId)
                .eq(Meeting::getPresetTypeCode, presetTypeCode)
                .in(Meeting::getStatus, NOT_STARTED)
                .orderByDesc(Meeting::getCreatedAt)
                .last("LIMIT 1"));
    }

    private Meeting findActiveMeeting(String creatorId) {
        return meetingMapper.selectOne(new LambdaQueryWrapper<Meeting>()
                .eq(Meeting::getCreatorId, creatorId)
                .in(Meeting::getStatus,
                        MeetingStatus.STARTED.name(),
                        MeetingStatus.RECORDING.name(),
                        MeetingStatus.PAUSED.name())
                .orderByDesc(Meeting::getCreatedAt)
                .last("LIMIT 1"));
    }

    private MeetingResponse returnExistingMeeting(String openId, String chatId, Meeting meeting, boolean inProgress) {
        String recordingToken = ensureRecordingToken(meeting.getId(), openId);
        String recordingUrl = meetingWebPageUrls.resolveRecordingPageUrl(
                meeting.getId(), recordingToken, meeting.getRecordingUrl());
        if (inProgress) {
            String card = cardBuilder.buildMeetingStartedNotifyCard(meeting.getId(), meeting.getTitle(), recordingUrl);
            sendCard(openId, chatId, card);
            pushOnlineParticipantJoinLinks(meeting.getId(), meeting.getTitle());
            log.warn("Detected existing active meeting, returned existing flow: openId={}, meetingId={}",
                    openId, meeting.getId());
        } else {
            sendCreatedNotifyCard(openId, chatId, meeting.getId(), meeting.getTitle(), recordingUrl);
            log.info("Reused existing draft meeting: openId={}, meetingId={}, preset={}",
                    openId, meeting.getId(), meeting.getPresetTypeCode());
        }
        return meetingService.getMeeting(meeting.getId());
    }

    private String ensureRecordingToken(String meetingId, String openId) {
        Meeting row = meetingMapper.selectById(meetingId);
        if (row != null && row.getRecordingToken() != null && !row.getRecordingToken().isBlank()) {
            return row.getRecordingToken();
        }
        String userName = feishuService.getUserNameByUserId(openId);
        String recordingToken = jwtUtil.generateOperatorMeetingToken(
                meetingId, JwtUtil.TYPE_RECORDING, openId, userName);
        meetingMapper.update(null, new LambdaUpdateWrapper<Meeting>()
                .eq(Meeting::getId, meetingId)
                .set(Meeting::getRecordingToken, recordingToken));
        return recordingToken;
    }

    private void sendCreatedNotifyCard(String openId, String chatId, String meetingId,
                                       String title, String recordingUrl) {
        String card = cardBuilder.buildMeetingCreatedNotifyCard(meetingId, title, recordingUrl);
        sendCard(openId, chatId, card);
    }

    private void sendCard(String openId, String chatId, String card) {
        boolean sentToChat = chatId != null && !chatId.isBlank() && feishuService.sendInteractiveCard(chatId, card);
        if (!sentToChat) {
            feishuService.sendInteractiveCardToUserId(openId, card);
        }
    }

    /**
     * instant-start：从 preset schedule_config 写入 scheduled_time（不读请求体）。
     */
    private void applyPresetScheduledTimeIfNeeded(String meetingId, Integer presetTypeCode) {
        if (presetTypeCode == null || presetTypeCode <= 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        presetScheduleTimeResolver.resolve(presetTypeCode, now).ifPresent(scheduledTime ->
                meetingMapper.update(null, new LambdaUpdateWrapper<Meeting>()
                        .eq(Meeting::getId, meetingId)
                        .set(Meeting::getScheduledTime, scheduledTime)));
    }

    /**
     * 向每位线上参会人单聊推送个人入会链接（含 open_id 的参会人）。
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
            boolean sent = feishuService.sendInteractiveCardToUserId(openId, personalCard);
            if (sent) {
                log.info("已推送个人入会链接: meetingId={}, openId={}, name={}",
                        meetingId, openId, p.getName());
            } else {
                log.warn("个人入会链接推送失败: meetingId={}, rawUserId={}, name={}",
                        meetingId, openId, p.getName());
            }
        }
    }
}

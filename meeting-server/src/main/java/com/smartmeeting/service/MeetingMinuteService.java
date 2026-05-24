package com.smartmeeting.service;

import com.smartmeeting.config.MeetingMinuteProperties;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.entity.MeetingMinute;
import com.smartmeeting.enums.MinuteGenerationStatus;
import com.smartmeeting.repository.MeetingMapper;
import com.smartmeeting.repository.MeetingMinuteMapper;
import com.smartmeeting.util.MeetingPresetTypeCodes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 会议纪要库内持久化服务：与飞书文档双写，飞书失败时库内仍可查询。
 * <p>
 * 主要协作组件：{@link MeetingMinuteMapper}、{@link MeetingMinuteProperties}。
 */
@Service
@RequiredArgsConstructor
public class MeetingMinuteService {

    private final MeetingMinuteMapper meetingMinuteMapper;
    private final MeetingMapper meetingMapper;
    private final MeetingMinuteProperties minuteProperties;

    /**
     * 保存或更新会议最新纪要（受 {@link MeetingMinuteProperties#isPersistEnabled()} 开关控制）。
     *
     * @param meetingId 会议 ID
     * @param markdown  纪要 Markdown 正文
     * @param status    生成状态（READY / PARTIAL / FAILED 等）
     */
    @Transactional
    public void saveLatest(String meetingId, String markdown, MinuteGenerationStatus status) {
        saveLatest(meetingId, markdown, status, null);
    }

    /**
     * 保存或更新会议最新纪要；可选写入正文对应文档链接（飞书纪要 URL 等）。
     *
     * @param contentUrl 正文链接，最长 300 字符；超长截断，null/空表示不更新已有链接
     */
    @Transactional
    public void saveLatest(String meetingId,
                           String markdown,
                           MinuteGenerationStatus status,
                           String contentUrl) {
        if (!minuteProperties.isPersistEnabled()) {
            return;
        }
        if (meetingId == null || meetingId.isBlank()) {
            return;
        }
        String body = markdown != null ? markdown : "";
        MeetingMinute row = new MeetingMinute();
        row.setMeetingId(meetingId.trim());
        row.setPresetTypeCode(resolvePresetTypeCode(meetingId));
        row.setContentMarkdown(body);
        row.setContentLength(body.length());
        row.setGenerationStatus(status != null ? status.name() : MinuteGenerationStatus.READY.name());
        row.setGeneratedAt(LocalDateTime.now());
        String url = normalizeContentUrl(contentUrl);
        if (url != null) {
            row.setContentUrl(url);
        }
        MeetingMinute existing = meetingMinuteMapper.selectById(row.getMeetingId());
        if (existing == null) {
            meetingMinuteMapper.insert(row);
        } else {
            if (url == null && existing.getContentUrl() != null) {
                row.setContentUrl(existing.getContentUrl());
            }
            if (row.getPresetTypeCode() == null && existing.getPresetTypeCode() != null) {
                row.setPresetTypeCode(existing.getPresetTypeCode());
            }
            meetingMinuteMapper.updateById(row);
        }
    }

    /**
     * 仅更新纪要正文对应链接（记录已存在时）。
     */
    @Transactional
    public void updateContentUrl(String meetingId, String contentUrl) {
        if (!minuteProperties.isPersistEnabled()) {
            return;
        }
        if (meetingId == null || meetingId.isBlank()) {
            return;
        }
        String url = normalizeContentUrl(contentUrl);
        if (url == null) {
            return;
        }
        MeetingMinute existing = meetingMinuteMapper.selectById(meetingId.trim());
        if (existing == null) {
            return;
        }
        MeetingMinute patch = new MeetingMinute();
        patch.setMeetingId(existing.getMeetingId());
        patch.setContentUrl(url);
        if (existing.getPresetTypeCode() == null) {
            patch.setPresetTypeCode(resolvePresetTypeCode(meetingId));
        }
        meetingMinuteMapper.updateById(patch);
    }

    private Integer resolvePresetTypeCode(String meetingId) {
        Meeting meeting = meetingMapper.selectById(meetingId.trim());
        return MeetingPresetTypeCodes.fromMeeting(meeting);
    }

    private static String normalizeContentUrl(String contentUrl) {
        if (contentUrl == null) {
            return null;
        }
        String u = contentUrl.trim();
        if (u.isEmpty()) {
            return null;
        }
        return u.length() > 300 ? u.substring(0, 300) : u;
    }

    /**
     * 查询会议最新纪要记录。
     *
     * @param meetingId 会议 ID
     * @return 纪要实体；不存在或 ID 为空时返回 {@link Optional#empty()}
     */
    public Optional<MeetingMinute> getLatest(String meetingId) {
        if (meetingId == null || meetingId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(meetingMinuteMapper.selectById(meetingId.trim()));
    }

    /**
     * 判断会议是否已有库内纪要记录。
     *
     * @param meetingId 会议 ID
     * @return 存在记录返回 {@code true}
     */
    public boolean exists(String meetingId) {
        return getLatest(meetingId).isPresent();
    }

    /**
     * 获取纪要 Markdown 正文，无记录或内容为空时返回空字符串。
     *
     * @param meetingId 会议 ID
     * @return 纪要正文或空字符串
     */
    public String getContentMarkdownOrEmpty(String meetingId) {
        return getLatest(meetingId)
                .map(MeetingMinute::getContentMarkdown)
                .filter(s -> s != null && !s.isBlank())
                .orElse("");
    }
}

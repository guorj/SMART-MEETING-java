package com.smartmeeting.service;

import com.smartmeeting.api.dto.MinuteResponse;
import com.smartmeeting.config.MeetingMinuteProperties;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.entity.MeetingMinute;
import com.smartmeeting.exception.BusinessException;
import com.smartmeeting.repository.MeetingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 会议纪要查询门面：聚合库内纪要与飞书文档元信息，按配置决定是否暴露正文。
 * <p>
 * 主要协作组件：{@link MeetingMapper}、{@link MeetingMinuteService}、{@link MeetingMinuteProperties}。
 */
@Service
@RequiredArgsConstructor
public class MeetingMinuteQueryService {

    private final MeetingMapper meetingMapper;
    private final MeetingMinuteService meetingMinuteService;
    private final MeetingMinuteProperties minuteProperties;

    /**
     * 查询指定会议的纪要信息（来源、文档链接、可选正文）。
     *
     * @param meetingId 会议 ID
     * @return 纪要响应 DTO（source 为 database / feishu_only / none）
     * @throws BusinessException 会议不存在时抛出 404
     */
    public MinuteResponse getMinuteForMeeting(String meetingId) {
        Meeting meeting = meetingMapper.selectById(meetingId);
        if (meeting == null) {
            throw new BusinessException(404, "会议不存在: " + meetingId);
        }
        Optional<MeetingMinute> stored = meetingMinuteService.getLatest(meetingId);
        String minuteUrl = stored.map(MeetingMinute::getContentUrl)
                .filter(s -> s != null && !s.isBlank())
                .orElse(null);
        // 主表 doc_url 为权威；content_url 仅 300 字且可能截断，仅在主表为空时回退
        String docUrl = preferDocUrl(minuteUrl, meeting.getDocUrl());
        String docToken = meeting.getDocToken();
        boolean hasDoc = docUrl != null && !docUrl.isBlank();
        boolean hasDb = stored.isPresent()
                && stored.get().getContentMarkdown() != null
                && !stored.get().getContentMarkdown().isBlank();

        String source;
        if (hasDb) {
            source = "database";
        } else if (hasDoc) {
            source = "feishu_only";
        } else {
            source = "none";
        }

        String content = null;
        if (hasDb && minuteProperties.isExposeContentInApi()) {
            content = stored.get().getContentMarkdown();
        }

        MinuteResponse.MinuteResponseBuilder builder = MinuteResponse.builder()
                .meetingId(meetingId)
                .source(source)
                .contentMarkdown(content)
                .docUrl(docUrl)
                .docToken(docToken)
                .hasMinute(hasDb || hasDoc);

        stored.ifPresent(m -> {
            builder.generationStatus(m.getGenerationStatus());
            builder.generatedAt(m.getGeneratedAt());
        });

        return builder.build();
    }

    /**
     * 优先主表 {@code doc_url}；仅当主表为空时使用纪要表 {@code content_url}（避免 300 字截断覆盖完整链接）。
     */
    static String preferDocUrl(String minuteContentUrl, String meetingDocUrl) {
        if (meetingDocUrl != null && !meetingDocUrl.isBlank()) {
            return meetingDocUrl.trim();
        }
        if (minuteContentUrl != null && !minuteContentUrl.isBlank()) {
            return minuteContentUrl.trim();
        }
        return null;
    }
}

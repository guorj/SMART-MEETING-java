package com.smartmeeting.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmeeting.admin.api.dto.MeetingMinuteViewDto;
import com.smartmeeting.admin.api.dto.TranscriptLineDto;
import com.smartmeeting.admin.entity.MeetingMinute;
import com.smartmeeting.admin.entity.TranscriptSegment;
import com.smartmeeting.admin.repository.MeetingMinuteMapper;
import com.smartmeeting.admin.repository.TranscriptSegmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ObservabilityAdminService {

    private final MeetingMinuteMapper minuteMapper;
    private final TranscriptSegmentMapper transcriptMapper;

    public MeetingMinuteViewDto getMinute(String meetingId) {
        MeetingMinute m = minuteMapper.selectById(meetingId);
        if (m == null) {
            return MeetingMinuteViewDto.builder().meetingId(meetingId).found(false).build();
        }
        return MeetingMinuteViewDto.builder()
                .meetingId(meetingId)
                .found(true)
                .generationStatus(m.getGenerationStatus())
                .contentUrl(m.getContentUrl())
                .contentLength(m.getContentLength())
                .contentMarkdown(m.getContentMarkdown())
                .generatedAt(m.getGeneratedAt())
                .build();
    }

    public List<TranscriptLineDto> listTranscripts(String meetingId, int limit, boolean finalsOnly) {
        int cap = Math.min(Math.max(limit, 1), 500);
        LambdaQueryWrapper<TranscriptSegment> q = new LambdaQueryWrapper<>();
        q.eq(TranscriptSegment::getMeetingId, meetingId);
        if (finalsOnly) {
            q.eq(TranscriptSegment::getIsFinal, true);
        }
        q.orderByAsc(TranscriptSegment::getStartTimeMs).last("LIMIT " + cap);
        return transcriptMapper.selectList(q).stream()
                .map(s -> TranscriptLineDto.builder()
                        .id(s.getId())
                        .speakerName(s.getSpeakerName())
                        .startTimeMs(s.getStartTimeMs())
                        .text(s.getText())
                        .isFinal(Boolean.TRUE.equals(s.getIsFinal()))
                        .confidence(s.getConfidence())
                        .build())
                .collect(Collectors.toList());
    }
}

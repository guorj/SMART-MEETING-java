package com.smartmeeting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmeeting.entity.TranscriptSegment;
import com.smartmeeting.repository.TranscriptMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * 转写分段查询与纪要文本拼装。
 */
@Component
@RequiredArgsConstructor
public class TranscriptSegmentHelper {

    private final TranscriptMapper transcriptMapper;

    /**
     * 是否存在会中实时 ASR 写入的定稿分段（用于跳过离线覆盖）。
     */
    public boolean hasFinalRealtimeSegments(String meetingId) {
        if (meetingId == null || meetingId.isBlank()) {
            return false;
        }
        Long count = transcriptMapper.selectCount(new LambdaQueryWrapper<TranscriptSegment>()
                .eq(TranscriptSegment::getMeetingId, meetingId)
                .eq(TranscriptSegment::getIsFinal, true));
        return count != null && count > 0;
    }

    /**
     * 按时间序读取定稿分段。
     */
    public List<TranscriptSegment> listFinalSegments(String meetingId) {
        return transcriptMapper.selectList(new LambdaQueryWrapper<TranscriptSegment>()
                .eq(TranscriptSegment::getMeetingId, meetingId)
                .eq(TranscriptSegment::getIsFinal, true)
                .orderByAsc(TranscriptSegment::getStartTimeMs));
    }

    /**
     * 拼装纪要用全文：优先 speakerName，否则 speakerId。
     */
    public String buildLabeledTranscriptText(List<TranscriptSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            return "";
        }
        segments.sort(Comparator.comparingInt(s -> s.getStartTimeMs() == null ? 0 : s.getStartTimeMs()));
        StringBuilder sb = new StringBuilder();
        for (TranscriptSegment seg : segments) {
            if (seg.getText() == null || seg.getText().isBlank()) {
                continue;
            }
            String label = seg.getSpeakerName();
            if (label == null || label.isBlank()) {
                label = seg.getSpeakerId() != null ? seg.getSpeakerId() : "speaker_unknown";
            }
            sb.append(label).append(": ").append(seg.getText().trim()).append("\n");
        }
        return sb.toString().trim();
    }
}

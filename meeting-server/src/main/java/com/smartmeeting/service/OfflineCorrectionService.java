package com.smartmeeting.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 会议录音离线 ASR 校正服务（委托 {@link OfflineTranscriptVoiceprintService}）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfflineCorrectionService {

    private final OfflineTranscriptVoiceprintService offlineTranscriptVoiceprintService;
    private final TranscriptSegmentHelper transcriptSegmentHelper;

    /**
     * 执行离线转写 + 可选声纹标注，返回带说话人标签的全文。
     * 若已有会中实时定稿分段，则返回空串（由调用方使用 DB 分段）。
     *
     * @param meetingId 会议 ID
     * @param audioPath 音频文件本地路径
     * @return 纪要用全文；跳过或失败时返回空字符串
     */
    @Transactional
    public String correct(String meetingId, String audioPath) {
        log.info("Starting offline correction for meeting: {}, audioPath={}", meetingId, audioPath);
        if (transcriptSegmentHelper.hasFinalRealtimeSegments(meetingId)) {
            log.info("Offline correction skipped (realtime segments exist): meetingId={}", meetingId);
            return transcriptSegmentHelper.buildLabeledTranscriptText(
                    transcriptSegmentHelper.listFinalSegments(meetingId));
        }
        return offlineTranscriptVoiceprintService.run(meetingId, audioPath);
    }
}

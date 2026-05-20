package com.smartmeeting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmeeting.asr.XfyunOfflineClient;
import com.smartmeeting.entity.TranscriptSegment;
import com.smartmeeting.repository.TranscriptMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * 会议录音离线 ASR 校正服务。
 *
 * <p>流程：校验音频文件 → 调用 {@link com.smartmeeting.asr.XfyunOfflineClient} 全文转写 →
 * 将已 finalize 的 {@link com.smartmeeting.entity.TranscriptSegment} 标记为已校正。
 *
 * <p>主要协作：{@link com.smartmeeting.repository.TranscriptMapper}、
 * {@link com.smartmeeting.asr.XfyunOfflineClient}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfflineCorrectionService {

    private final TranscriptMapper transcriptMapper;
    private final XfyunOfflineClient xfyunOfflineClient;

    /**
     * 执行离线校正
     * 
     * @param meetingId 会议ID
     * @param audioPath 音频文件本地路径
     * @return 拼接后的校正全文；文件缺失、为空或 ASR 失败时返回空字符串（不抛异常）
     */
    @Transactional
    public String correct(String meetingId, String audioPath) {
        log.info("Starting offline correction for meeting: {}, audioPath={}", meetingId, audioPath);

        // 1. 验证音频文件存在
        if (audioPath == null || !Files.exists(Paths.get(audioPath))) {
            log.warn("Audio file not found: {}", audioPath);
            return "";
        }

        try {
            // 2. 获取文件大小
            long fileSize = Files.size(Paths.get(audioPath));
            log.info("Audio file size: {} bytes", fileSize);

            if (fileSize == 0) {
                log.warn("Audio file is empty: {}", audioPath);
                return "";
            }

            // 3. 调用讯飞离线 ASR 校正
            List<TranscriptSegment> segments = xfyunOfflineClient.transcribe(audioPath);
            
            if (segments == null || segments.isEmpty()) {
                log.warn("Offline ASR returned empty result for meeting: {}", meetingId);
                return "";
            }
            
            // 拼接所有分段文本
            StringBuilder textBuilder = new StringBuilder();
            for (TranscriptSegment seg : segments) {
                if (seg.getText() != null) {
                    textBuilder.append(seg.getText()).append(" ");
                }
            }
            String correctedText = textBuilder.toString().trim();

            log.info("Offline correction completed for meeting: {}, text length={}", 
                    meetingId, correctedText.length());

            // 4. 更新现有的 transcript_segment 标记为已校正
            updateSegmentsAsCorrected(meetingId);

            // 5. 如果离线 ASR 返回了分段结果，更新 transcript
            // TODO: 解析离线 ASR 的分段结果并更新 transcript_segment

            return correctedText;

        } catch (IOException e) {
            log.error("Failed to read audio file: {}", audioPath, e);
            return "";
        } catch (Exception e) {
            log.error("Offline correction failed for meeting: {}", meetingId, e);
            return "";
        }
    }

    /**
     * 将会议下所有已 finalize 的转录分段标记为 {@code corrected=true}。
     *
     * @param meetingId 会议 ID
     */
    private void updateSegmentsAsCorrected(String meetingId) {
        LambdaQueryWrapper<TranscriptSegment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TranscriptSegment::getMeetingId, meetingId);
        wrapper.eq(TranscriptSegment::getIsFinal, true);

        List<TranscriptSegment> segments = transcriptMapper.selectList(wrapper);
        for (TranscriptSegment segment : segments) {
            segment.setCorrected(true);
            transcriptMapper.updateById(segment);
        }

        log.info("Marked {} segments as corrected for meeting: {}", segments.size(), meetingId);
    }
}

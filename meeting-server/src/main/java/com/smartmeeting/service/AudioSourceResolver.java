package com.smartmeeting.service;

import com.smartmeeting.config.MeetingVcProperties;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.repository.MeetingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

/**
 * 会后音频源选择：有妙记 token（File B）则用 File B，否则回退 File A（浏览器 PCM）。
 * <p>
 * 策略简化（不阻塞等待 webhook）：
 * <ol>
 *   <li>{@code meeting.vc.recording-enabled=false} → 直接返回 null（调用方走 File A 逻辑）</li>
 *   <li>{@code vc_minute_token} 为空 → 返回 null（webhook 未到，走 File A）</li>
 *   <li>调 {@link FeishuMinutesService#downloadAndTranscodeToPcm} 下载转码</li>
 *   <li>成功 → 返回 {meetingId}_vc.pcm 路径，source=vc_recording</li>
 *   <li>失败 → 返回 null（告警，回退 File A）</li>
 * </ol>
 * <p>
 * 不做 A+B 合并。主持人未入飞书的极端场景仍用 File A，转写覆盖不全可接受。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AudioSourceResolver {

    private final MeetingMapper meetingMapper;
    private final FeishuMinutesService feishuMinutesService;
    private final MeetingVcProperties vcProperties;

    /** 解析结果。 */
    public record AudioSource(Path path, String source) {
        public static final String SOURCE_VC_RECORDING = "vc_recording";
    }

    /**
     * 解析会议的最终音频源。
     *
     * @param meetingId 会议 ID
     * @return AudioSource（path + source）；返回 null 表示走 File A 回退逻辑
     */
    public AudioSource resolve(String meetingId) {
        if (!vcProperties.isRecordingEnabled()) {
            return null;
        }
        Meeting meeting = meetingMapper.selectById(meetingId);
        if (meeting == null) {
            return null;
        }
        String token = meeting.getVcMinuteToken();
        if (token == null || token.isBlank()) {
            log.debug("AudioSourceResolver no vc_minute_token, fallback File A: meetingId={}", meetingId);
            return null;
        }
        log.info("AudioSourceResolver downloading File B: meetingId={}, minuteToken={}", meetingId, token);
        Path pcm = feishuMinutesService.downloadAndTranscodeToPcm(meetingId, token);
        if (pcm == null) {
            log.warn("AudioSourceResolver File B download failed, fallback File A: meetingId={}", meetingId);
            return null;
        }
        return new AudioSource(pcm, AudioSource.SOURCE_VC_RECORDING);
    }
}

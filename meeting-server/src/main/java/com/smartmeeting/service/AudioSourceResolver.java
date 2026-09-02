package com.smartmeeting.service;

import com.smartmeeting.config.MeetingVcProperties;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.repository.MeetingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

/**
 * 会后音频源选择：有妙记 token（File B）则下载转码；无 token 时由 {@link PostMeetingOrchestrator}
 * 决定是否等待 VC 回调，本类不负责 File A 回退。
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

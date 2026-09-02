package com.smartmeeting.service;

import com.smartmeeting.config.MeetingVcProperties;
import com.smartmeeting.entity.Meeting;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 飞书 VC 会后音频策略：有 VC 时优先等妙记（File B），无 VC 时直接用浏览器 PCM（File A）。
 */
@Component
@RequiredArgsConstructor
public class VcRecordingPostMeetingPolicy {

    private final MeetingVcProperties vcProperties;

    /**
     * 会议通过日历/Open API 创建了飞书 VC，且云端录制总开关已开。
     */
    public boolean expectsVcRecording(Meeting meeting) {
        if (meeting == null || !vcProperties.isRecordingEnabled()) {
            return false;
        }
        String url = meeting.getVcMeetingUrl();
        return url != null && !url.isBlank();
    }

    /**
     * 已建 VC 但 {@code vc_minute_token} 尚未落库（webhook 未到或 Admin 未补录）。
     */
    public boolean isAwaitingVcToken(Meeting meeting) {
        if (!expectsVcRecording(meeting)) {
            return false;
        }
        String token = meeting.getVcMinuteToken();
        return token == null || token.isBlank();
    }

    /**
     * 会议结束已超过 {@code meeting.vc.callback-timeout-min}，可回退 File A。
     */
    public boolean isPastCallbackTimeout(Meeting meeting) {
        if (meeting == null || meeting.getActualEndTime() == null) {
            return false;
        }
        LocalDateTime deadline = meeting.getActualEndTime()
                .plusMinutes(Math.max(1, vcProperties.getCallbackTimeoutMin()));
        return !LocalDateTime.now().isBefore(deadline);
    }
}

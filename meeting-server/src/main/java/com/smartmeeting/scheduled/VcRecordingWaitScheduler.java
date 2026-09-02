package com.smartmeeting.scheduled;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmeeting.config.MeetingVcProperties;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.enums.MeetingStatus;
import com.smartmeeting.repository.MeetingMapper;
import com.smartmeeting.service.PostMeetingOrchestrator;
import com.smartmeeting.service.VcRecordingPostMeetingPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 扫描等待飞书 VC {@code recording_ready} 的会议；超时后回退 File A 继续离线转写。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VcRecordingWaitScheduler {

    private final MeetingMapper meetingMapper;
    private final MeetingVcProperties vcProperties;
    private final VcRecordingPostMeetingPolicy vcRecordingPolicy;
    private final PostMeetingOrchestrator postMeetingOrchestrator;

    @Scheduled(fixedDelayString = "${meeting.vc.wait-scan-ms:60000}")
    public void scanAwaitingVcRecording() {
        if (!vcProperties.isRecordingEnabled()) {
            return;
        }
        int timeoutMin = Math.max(1, vcProperties.getCallbackTimeoutMin());
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(timeoutMin);

        List<Meeting> candidates = meetingMapper.selectList(new LambdaQueryWrapper<Meeting>()
                .eq(Meeting::getStatus, MeetingStatus.PROCESSING.name())
                .isNotNull(Meeting::getVcMeetingUrl)
                .isNull(Meeting::getVcMinuteToken)
                .isNotNull(Meeting::getActualEndTime)
                .lt(Meeting::getActualEndTime, cutoff));

        for (Meeting meeting : candidates) {
            if (!vcRecordingPolicy.isAwaitingVcToken(meeting)) {
                continue;
            }
            try {
                postMeetingOrchestrator.resumePostMeetingWithFileAFallback(meeting.getId());
            } catch (Exception e) {
                log.warn("VC wait timeout fallback failed: meetingId={}, err={}", meeting.getId(), e.getMessage());
            }
        }
    }
}

package com.smartmeeting.scheduled;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.enums.MeetingStatus;
import com.smartmeeting.repository.MeetingMapper;
import com.smartmeeting.service.MeetingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 录音超时检查定时任务
 *
 * 规则:
 * - 每5分钟检查一次
 * - 检查状态 RECORDING/PAUSED 且实际开始时间超过4小时的会议
 * - 自动结束超时会议 → 触发纪要生成链
 *
 * 原因:
 * - 用户忘记结束录音
 * - 网络断开导致前端无法发送结束指令
 * - 浏览器崩溃导致录音中断
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "meeting.recording.timeout-check-enabled", havingValue = "true", matchIfMissing = true)
public class RecordingTimeoutChecker {

    private final MeetingMapper meetingMapper;
    private final MeetingService meetingService;

    /** 最大录音时长（小时） */
    private static final int MAX_DURATION_HOURS = 4;

    /**
     * 每5分钟检查录音超时
     */
    @Scheduled(fixedRate = 300000) // 5分钟
    public void checkTimeout() {
        log.debug("Checking recording timeouts...");

        LocalDateTime timeoutThreshold = LocalDateTime.now().minusHours(MAX_DURATION_HOURS);

        // 查询超时会议（须括号：(RECORDING OR PAUSED) AND startTime < threshold）
        LambdaQueryWrapper<Meeting> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.eq(Meeting::getStatus, MeetingStatus.RECORDING.name())
                        .or()
                        .eq(Meeting::getStatus, MeetingStatus.PAUSED.name()))
                .lt(Meeting::getActualStartTime, timeoutThreshold);

        List<Meeting> timeoutMeetings = meetingMapper.selectList(wrapper);

        if (timeoutMeetings.isEmpty()) {
            log.debug("No timeout meetings found");
            return;
        }

        log.warn("Found {} timeout meetings, will auto-end them", timeoutMeetings.size());

        for (Meeting meeting : timeoutMeetings) {
            try {
                log.warn("Auto-ending timeout meeting: id={}, title={}, started={}",
                        meeting.getId(), meeting.getTitle(), meeting.getActualStartTime());

                // 调用 endMeeting 触发完整流程
                meetingService.endMeeting(meeting.getId());

                log.info("Timeout meeting ended successfully: id={}", meeting.getId());

            } catch (Exception e) {
                log.error("Failed to auto-end timeout meeting: id={}, error={}",
                        meeting.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * 每小时统计录音状态
     */
    @Scheduled(cron = "0 0 * * * ?") // 每小时
    public void reportRecordingStatus() {
        LambdaQueryWrapper<Meeting> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Meeting::getStatus, MeetingStatus.RECORDING.name())
                .or()
                .eq(Meeting::getStatus, MeetingStatus.PAUSED.name());

        long activeCount = meetingMapper.selectCount(wrapper);

        if (activeCount > 0) {
            log.info("Currently {} meetings in recording/paused state", activeCount);
        } else {
            log.debug("No active recordings");
        }
    }
}
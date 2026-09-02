package com.smartmeeting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.smartmeeting.config.MeetingVcProperties;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.repository.MeetingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 处理飞书 {@code vc.meeting.recording_ready_v1} 回调。
 * <p>
 * 流程：
 * <ol>
 *   <li>从 {@code event.url} 提取 minute_token</li>
 *   <li>匹配 {@code int_meeting}（优先 vc_meeting_url 会议号，其次 title + 时间窗口）</li>
 *   <li>写入 {@code vc_minute_token} + {@code vc_recording_url}</li>
 * </ol>
 * <p>
 * 不阻塞 PostMeetingOrchestrator：回调迟到时仅落库，会议结束后由 Orchestrator 取已落库 token 走 File B；
 * 若会议已结束且 File A 已转写，Admin 可通过「重新生成纪要」补走 File B。
 * <p>
 * 事件体关键字段（schema 2.0）：
 * <ul>
 *   <li>{@code header.event_type} = "vc.meeting.recording_ready_v1"</li>
 *   <li>{@code event.meeting.id} 飞书 VC meeting_id</li>
 *   <li>{@code event.url} 妙记链接，含 minute_token</li>
 *   <li>{@code event.duration} 录制时长（毫秒）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeishuMinutesCallbackHandler {

    private static final int MATCH_WINDOW_HOURS = 2;

    private final MeetingMapper meetingMapper;
    private final MeetingVcProperties vcProperties;
    private final PostMeetingOrchestrator postMeetingOrchestrator;

    public void handleRecordingReady(JsonNode body) {
        if (!vcProperties.isRecordingEnabled()) {
            log.debug("recording_ready_v1 ignored (recording-enabled=false)");
            return;
        }
        JsonNode event = body.path("event");
        String recordingUrl = event.path("url").asText("");
        String meetingId = event.path("meeting").path("id").asText("");
        long durationMs = event.path("duration").asLong(0);

        if (recordingUrl.isBlank()) {
            log.warn("recording_ready_v1 missing event.url, body={}", body);
            return;
        }

        String minuteToken = FeishuMinutesService.extractMinuteToken(recordingUrl);
        if (minuteToken.isBlank()) {
            log.warn("recording_ready_v1 cannot extract minute_token from url={}", recordingUrl);
            return;
        }

        log.info("recording_ready_v1 received: vcMeetingId={}, minuteToken={}, durationMs={}, url={}",
                meetingId, minuteToken, durationMs, recordingUrl);

        Meeting meeting = matchMeeting(meetingId, recordingUrl, event);
        if (meeting == null) {
            log.warn("recording_ready_v1 no matching meeting: vcMeetingId={}, url={}", meetingId, recordingUrl);
            return;
        }

        meeting.setVcMinuteToken(minuteToken);
        meeting.setVcRecordingUrl(recordingUrl);
        meetingMapper.updateById(meeting);
        log.info("recording_ready_v1 persisted: meetingId={}, minuteToken={}", meeting.getId(), minuteToken);
        postMeetingOrchestrator.resumePostMeetingAfterVcReady(meeting.getId());
    }

    /**
     * 匹配 int_meeting。优先级：
     * <ol>
     *   <li>vc_meeting_url 中会议号与 event.meeting.id 匹配</li>
     *   <li>title 与 event.meeting.topic 模糊匹配 + scheduled_time 时间窗口</li>
     *   <li>近期（±2h）有 vc_meeting_url 的会议</li>
     * </ol>
     */
    private Meeting matchMeeting(String vcMeetingId, String recordingUrl, JsonNode event) {
        // 1. vc_meeting_url 中含会议号（vc.feishu.cn/j/{no}）；event.meeting.id 是飞书内部 ID，不直接匹配
        //    退化策略：扫描近期有 vc_meeting_url 且未结束的会议
        if (vcMeetingId != null && !vcMeetingId.isBlank()) {
            // 飞书 event.meeting.id 与 vc_meeting_url 的会议号不是同一字段，无法直接关联
            // 留作后续若申请到 vc:meeting:readonly 后通过 API 反查
        }

        // 2. topic + 时间窗口
        String topic = event.path("meeting").path("topic").asText("");
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = now.minusHours(MATCH_WINDOW_HOURS);
        LocalDateTime to = now.plusHours(MATCH_WINDOW_HOURS);

        LambdaQueryWrapper<Meeting> wrapper = new LambdaQueryWrapper<Meeting>()
                .isNotNull(Meeting::getScheduledTime)
                .between(Meeting::getScheduledTime, from, to)
                .orderByAsc(Meeting::getScheduledTime);
        List<Meeting> candidates = meetingMapper.selectList(wrapper);
        if (candidates.isEmpty()) {
            return null;
        }

        // 2a. 优先匹配 title 包含 topic（或反之）
        if (!topic.isBlank()) {
            for (Meeting m : candidates) {
                String title = m.getTitle() == null ? "" : m.getTitle();
                if (title.contains(topic) || topic.contains(title)) {
                    return m;
                }
            }
        }

        // 2b. 优先有 vc_meeting_url 的（说明走了 Open API 预约，回调大概率对应它）
        for (Meeting m : candidates) {
            if (m.getVcMeetingUrl() != null && !m.getVcMeetingUrl().isBlank()) {
                return m;
            }
        }

        // 2c. 兜底取第一条
        return candidates.get(0);
    }
}

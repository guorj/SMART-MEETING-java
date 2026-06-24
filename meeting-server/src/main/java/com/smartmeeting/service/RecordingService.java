package com.smartmeeting.service;

import com.smartmeeting.entity.Meeting;
import com.smartmeeting.entity.Participant;
import com.smartmeeting.enums.MeetingStatus;
import com.smartmeeting.exception.BusinessException;
import com.smartmeeting.repository.MeetingMapper;
import com.smartmeeting.repository.ParticipantMapper;
import com.smartmeeting.statemachine.MeetingEvent;
import com.smartmeeting.statemachine.MeetingStateMachineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会议录音生命周期与状态机管理服务。
 *
 * <p>内存态跟踪 {@link RecordingState}（RECORDING / PAUSED），持久化会议状态与音频路径；
 * 停止录音后将会议置为 PROCESSING 并发布领域事件，经 Outbox 异步触发纪要链路。
 *
 * <p>状态流转：STARTED/REVIEWING → RECORDING ⇄ PAUSED →（停止）PROCESSING。
 *
 * <p>主要协作：{@link com.smartmeeting.repository.MeetingMapper}、
 * {@link com.smartmeeting.repository.ParticipantMapper}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordingService {

    private final MeetingMapper meetingMapper;
    private final ParticipantMapper participantMapper;
    private final AudioCacheService audioCacheService;

    @Value("${meeting.llm.model:deepseek-v4-pro}")
    private String modelName;

    private final MeetingStateMachineService meetingStateMachineService;
    private final PostMeetingOrchestrator postMeetingOrchestrator;

    // 会议ID → 录音状态追踪
    private final Map<String, RecordingState> recordingStates = new ConcurrentHashMap<>();

    /**
     * 开始或恢复录音；若内存态为 PAUSED 则等同 {@link #resumeRecording}。
     *
     * @param meetingId 会议 ID
     * @return 音频文件绝对/相对路径
     * @throws BusinessException 会议不存在（404）、状态不允许（400）或已在录音中（400）
     */
    @Transactional
    public String startRecording(String meetingId) {
        Meeting meeting = meetingMapper.selectById(meetingId);
        if (meeting == null) {
            throw new BusinessException(404, "会议不存在: " + meetingId);
        }

        // 验证状态
        String currentStatus = meeting.getStatus();
        if (!MeetingStatus.STARTED.name().equals(currentStatus) 
                && !MeetingStatus.REVIEWING.name().equals(currentStatus)) {
            throw new BusinessException(400, "会议状态不是 STARTED 或 REVIEWING，无法开始录音: " + currentStatus);
        }

        // 检查是否已有录音
        if (recordingStates.containsKey(meetingId)) {
            RecordingState state = recordingStates.get(meetingId);
            if (state == RecordingState.RECORDING) {
                throw new BusinessException(400, "会议已在录音中");
            }
            // 如果是暂停状态，继续录音
            return resumeRecording(meetingId);
        }

        String audioPath = generateAudioPath(meetingId);
        meeting.setAudioPath(audioPath);
        meetingStateMachineService.apply(meetingId, MeetingEvent.START_RECORDING);
        meeting.setStatus(MeetingStatus.RECORDING.name());
        meetingMapper.updateById(meeting);

        recordingStates.put(meetingId, RecordingState.RECORDING);

        // 确保目录存在；cache 可能已通过 WebSocket 先行写入，勿覆盖
        try {
            Path path = Paths.get(audioPath);
            Files.createDirectories(path.getParent());
            if (!Files.exists(path)) {
                Files.createFile(path);
            }
        } catch (IOException e) {
            throw new BusinessException("Failed to create audio file: " + e.getMessage());
        }

        log.info("Recording started for meeting: {}, audio path: {}", meetingId, audioPath);
        return audioPath;
    }

    /**
     * 暂停录音（仅更新内存态，会议 DB 状态仍为 RECORDING）。
     *
     * @param meetingId 会议 ID
     * @throws BusinessException 会议不存在（404）或未在录音中（400）
     */
    @Transactional
    public void pauseRecording(String meetingId) {
        Meeting meeting = meetingMapper.selectById(meetingId);
        if (meeting == null) {
            throw new BusinessException(404, "会议不存在: " + meetingId);
        }

        RecordingState state = recordingStates.get(meetingId);
        if (state != RecordingState.RECORDING) {
            throw new BusinessException(400, "会议未在录音中，无法暂停");
        }

        recordingStates.put(meetingId, RecordingState.PAUSED);
        log.info("Recording paused for meeting: {}", meetingId);
    }

    /**
     * 从暂停恢复录音。
     *
     * @param meetingId 会议 ID
     * @return 既有音频路径
     * @throws BusinessException 会议不存在（404）或未处于暂停态（400）
     */
    @Transactional
    public String resumeRecording(String meetingId) {
        Meeting meeting = meetingMapper.selectById(meetingId);
        if (meeting == null) {
            throw new BusinessException(404, "会议不存在: " + meetingId);
        }

        RecordingState state = recordingStates.get(meetingId);
        if (state != RecordingState.PAUSED) {
            throw new BusinessException(400, "会议未暂停，无法继续");
        }

        recordingStates.put(meetingId, RecordingState.RECORDING);
        log.info("Recording resumed for meeting: {}", meetingId);
        return meeting.getAudioPath();
    }

    /**
     * 停止录音：写回 PROCESSING、计算时长并发送纪要生成事件。
     *
     * @param meetingId 会议 ID
     * @return 含 {@code meetingId}、{@code status}、{@code audioPath}、{@code durationSeconds}、{@code fileSize} 的 Map
     * @throws BusinessException 会议不存在（404）或未开始录音（400）
     */
    @Transactional
    public Map<String, Object> stopRecording(String meetingId) {
        Meeting meeting = meetingMapper.selectById(meetingId);
        if (meeting == null) {
            throw new BusinessException(404, "会议不存在: " + meetingId);
        }

        RecordingState state = recordingStates.get(meetingId);
        String currentStatus = meeting.getStatus();
        if (MeetingStatus.PROCESSING.name().equals(currentStatus)
                || MeetingStatus.COMPLETED.name().equals(currentStatus)
                || MeetingStatus.TODO_TRACKING.name().equals(currentStatus)) {
            recordingStates.remove(meetingId);
            log.info("Recording stop skipped, meeting already ended: id={}, status={}", meetingId, currentStatus);
            return Map.of(
                    "meetingId", meetingId,
                    "status", currentStatus,
                    "audioPath", meeting.getAudioPath() != null ? meeting.getAudioPath() : "",
                    "durationSeconds", meeting.getDurationSeconds() != null ? meeting.getDurationSeconds() : 0,
                    "fileSize", 0L
            );
        }
        if (state == null) {
            throw new BusinessException(400, "会议未开始录音");
        }

        String audioPath = meeting.getAudioPath();

        // 获取音频文件信息
        long fileSize = 0;
        try {
            Path path = Paths.get(audioPath);
            if (Files.exists(path)) {
                fileSize = Files.size(path);
            }
        } catch (IOException e) {
            log.warn("Failed to get audio file size: {}", audioPath, e);
        }

        // 清理状态
        recordingStates.remove(meetingId);

        // 更新会议状态 → PROCESSING
        meetingStateMachineService.apply(meetingId, MeetingEvent.END_MEETING);
        meeting.setStatus(MeetingStatus.PROCESSING.name());
        meeting.setActualEndTime(LocalDateTime.now());
        if (meeting.getActualStartTime() != null) {
            meeting.setDurationSeconds((int) java.time.Duration.between(
                    meeting.getActualStartTime(), meeting.getActualEndTime()).getSeconds());
        }
        meetingMapper.updateById(meeting);

        log.info("Recording stopped for meeting: {}, status=PROCESSING, duration={}s, size={} bytes",
                meetingId, meeting.getDurationSeconds(), fileSize);

        // 获取参会人声纹特征ID列表
        List<Participant> participants = getParticipants(meetingId);
        List<String> featureIds = participants.stream()
                .map(Participant::getFeatureId)
                .filter(fid -> fid != null && !fid.isEmpty())
                .toList();

        String finalStatus = postMeetingOrchestrator.dispatchAfterMeetingEnded(
                meetingId, audioPath, featureIds, modelName);

        return Map.of(
                "meetingId", meetingId,
                "status", finalStatus,
                "audioPath", audioPath,
                "durationSeconds", meeting.getDurationSeconds(),
                "fileSize", fileSize
        );
    }

    /**
     * 获取会议参会人
     */
    private List<Participant> getParticipants(String meetingId) {
        return participantMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Participant>()
                        .eq(Participant::getMeetingId, meetingId));
    }

    /**
     * 获取内存中的录音会话状态。
     *
     * @param meetingId 会议 ID
     * @return 当前状态；未开始录音时返回 {@code null}
     */
    public RecordingState getRecordingState(String meetingId) {
        return recordingStates.get(meetingId);
    }

    /** 会议已通过其他入口结束时，清理进程内录音会话态。 */
    public void clearRecordingState(String meetingId) {
        recordingStates.remove(meetingId);
    }

    /** 按缓存根目录、当日日期与会议 ID 生成 PCM 文件路径。 */
    private String generateAudioPath(String meetingId) {
        return audioCacheService.cachePathFor(meetingId, LocalDate.now());
    }

    /**
     * 在 WebSocket 竞态（Audio 先于 FAST_START）或重连场景下尽力启动录音。
     *
     * @param meetingId 会议 ID
     * @return 音频路径；状态不允许且 cache 亦无数据时返回 {@code null}
     */
    @Transactional
    public String ensureRecordingStarted(String meetingId) {
        RecordingState state = recordingStates.get(meetingId);
        if (state == RecordingState.RECORDING || state == RecordingState.PAUSED) {
            Meeting meeting = meetingMapper.selectById(meetingId);
            return meeting != null ? meeting.getAudioPath() : null;
        }
        try {
            return startRecording(meetingId);
        } catch (BusinessException e) {
            return attachCachedAudioRecording(meetingId).orElse(null);
        }
    }

    /**
     * 解析会议音频路径：DB / 云端 URL；若 PCM 已在 cache 落盘则回填 {@code audio_path}。
     *
     * @param meeting 会议实体（可为 null）
     * @return 可用于离线 ASR 的路径或 URL；不可解析时 {@code null}
     */
    @Transactional
    public String resolveAndPersistAudioPath(Meeting meeting) {
        if (meeting == null) {
            return null;
        }
        if (meeting.getAudioPath() != null && !meeting.getAudioPath().isBlank()) {
            return meeting.getAudioPath();
        }
        if (meeting.getSourceAudioUrl() != null && !meeting.getSourceAudioUrl().isBlank()) {
            return meeting.getSourceAudioUrl();
        }
        Optional<String> cached = attachCachedAudioRecording(meeting.getId());
        return cached.orElse(null);
    }

    /** 若 cache 已有 PCM，将路径写入 DB 并注册内存录音态（不截断已有文件）。 */
    private Optional<String> attachCachedAudioRecording(String meetingId) {
        Optional<String> cached = audioCacheService.findExistingCachePath(meetingId);
        if (cached.isEmpty()) {
            return Optional.empty();
        }
        Meeting meeting = meetingMapper.selectById(meetingId);
        if (meeting == null) {
            return Optional.empty();
        }
        String audioPath = cached.get();
        if (meeting.getAudioPath() == null || meeting.getAudioPath().isBlank()) {
            meeting.setAudioPath(audioPath);
        }
        String status = meeting.getStatus();
        if (MeetingStatus.STARTED.name().equals(status) || MeetingStatus.REVIEWING.name().equals(status)) {
            meetingStateMachineService.apply(meetingId, MeetingEvent.START_RECORDING);
            meeting.setStatus(MeetingStatus.RECORDING.name());
        }
        meetingMapper.updateById(meeting);
        recordingStates.putIfAbsent(meetingId, RecordingState.RECORDING);
        log.info("Attached cached audio for meeting: {}, path={}", meetingId, audioPath);
        return Optional.of(meeting.getAudioPath());
    }

    /**
     * 从数据库读取会议已记录的音频路径。
     *
     * @param meetingId 会议 ID
     * @return 音频路径；会议不存在时返回 {@code null}
     */
    public String getAudioPath(String meetingId) {
        Meeting meeting = meetingMapper.selectById(meetingId);
        return meeting != null ? meeting.getAudioPath() : null;
    }

    /** 进程内录音会话状态（与 DB {@code Meeting.status} 配合使用）。 */
    public enum RecordingState {
        /** 正在录音 */
        RECORDING,
        /** 已暂停（可 resume） */
        PAUSED
    }
}

package com.smartmeeting.service;

import com.smartmeeting.entity.Meeting;
import com.smartmeeting.entity.Participant;
import com.smartmeeting.enums.MeetingStatus;
import com.smartmeeting.exception.BusinessException;
import com.smartmeeting.mq.KafkaProducer;
import com.smartmeeting.mq.LocalEventBus;
import com.smartmeeting.model.MinuteGenerateMessage;
import com.smartmeeting.repository.MeetingMapper;
import com.smartmeeting.repository.ParticipantMapper;
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
import java.util.concurrent.ConcurrentHashMap;

/**
 * 录音状态管理服务
 * 状态机: STARTED → RECORDING → PAUSED ↔ RECORDING → STOPPED → PROCESSING
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordingService {

    private final MeetingMapper meetingMapper;
    private final ParticipantMapper participantMapper;

    // Kafka 生产者（生产环境）
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private KafkaProducer kafkaProducer;

    // 本地事件总线（开发环境）
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private LocalEventBus localEventBus;

    @Value("${meeting.audio.cache-dir:/data/audio}")
    private String audioCacheDir;

    @Value("${meeting.llm.model:deepseek-v4-pro}")
    private String modelName;

    // 会议ID → 录音状态追踪
    private final Map<String, RecordingState> recordingStates = new ConcurrentHashMap<>();

    /**
     * 开始录音
     * 状态: STARTED/REVIEWING → RECORDING
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
        meeting.setStatus(MeetingStatus.RECORDING.name());
        meetingMapper.updateById(meeting);

        recordingStates.put(meetingId, RecordingState.RECORDING);

        // 确保目录存在
        try {
            Files.createDirectories(Paths.get(audioPath).getParent());
            // 创建空文件
            Files.createFile(Paths.get(audioPath));
        } catch (IOException e) {
            throw new BusinessException("Failed to create audio file: " + e.getMessage());
        }

        log.info("Recording started for meeting: {}, audio path: {}", meetingId, audioPath);
        return audioPath;
    }

    /**
     * 暂停录音
     * 状态: RECORDING → PAUSED
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
     * 继续录音
     * 状态: PAUSED → RECORDING
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
     * 停止录音
     * 状态: RECORDING/PAUSED → PROCESSING
     * 触发: 更新会议状态 → 发送 Kafka/LocalEventBus 事件 → 触发纪要生成链
     */
    @Transactional
    public Map<String, Object> stopRecording(String meetingId) {
        Meeting meeting = meetingMapper.selectById(meetingId);
        if (meeting == null) {
            throw new BusinessException(404, "会议不存在: " + meetingId);
        }

        RecordingState state = recordingStates.get(meetingId);
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

        // 发送纪要生成事件
        sendMinuteGenerateEvent(meetingId, audioPath, featureIds);

        return Map.of(
                "meetingId", meetingId,
                "status", "PROCESSING",
                "audioPath", audioPath,
                "durationSeconds", meeting.getDurationSeconds(),
                "fileSize", fileSize
        );
    }

    /**
     * 发送纪要生成事件到 Kafka 或 LocalEventBus
     */
    private void sendMinuteGenerateEvent(String meetingId, String audioPath, List<String> featureIds) {
        MinuteGenerateMessage message = MinuteGenerateMessage.builder()
                .meetingId(meetingId)
                .audioPath(audioPath)
                .featureIds(featureIds)
                .modelName(modelName)
                .sentAt(System.currentTimeMillis())
                .build();

        if (kafkaProducer != null) {
            // 生产环境：发送到 Kafka
            kafkaProducer.sendMinuteGenerate("meeting.events", message);
            log.info("Sent minute generate event to Kafka: meetingId={}", meetingId);
        } else if (localEventBus != null) {
            // 开发环境：通过本地事件总线处理
            localEventBus.publishMeetingEvent(message);
            log.info("Sent minute generate event to LocalEventBus: meetingId={}", meetingId);
        } else {
            log.warn("No event bus available for meeting: {}", meetingId);
        }
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
     * 获取录音状态
     */
    public RecordingState getRecordingState(String meetingId) {
        return recordingStates.get(meetingId);
    }

    /**
     * 生成音频文件路径
     */
    private String generateAudioPath(String meetingId) {
        String dateStr = LocalDate.now().toString();
        return audioCacheDir + "/" + dateStr + "/" + meetingId + ".pcm";
    }

    /**
     * 获取音频文件路径
     */
    public String getAudioPath(String meetingId) {
        Meeting meeting = meetingMapper.selectById(meetingId);
        return meeting != null ? meeting.getAudioPath() : null;
    }

    /**
     * 录音内部状态
     */
    public enum RecordingState {
        RECORDING,   // 正在录音
        PAUSED       // 已暂停
    }
}

package com.smartmeeting.service;

import com.smartmeeting.api.config.AudioWebSocketHandler;
import com.smartmeeting.asr.AsrResult;
import com.smartmeeting.asr.XfyunRealtimeClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.smartmeeting.repository.TranscriptMapper;
import com.smartmeeting.entity.TranscriptSegment;
import java.util.UUID;
/**
 * ASR 桥接服务 - 浏览器音频 → 后端 → 讯飞实时ASR
 * 
 * 音频流路径:
 * 浏览器 → WebSocket → AudioWebSocketHandler → AsrBridgeService → XfyunRealtimeClient
 * 转写结果: XfyunRealtimeClient → AsrBridgeService → AudioWebSocketHandler → 浏览器
 * 
 * 关键：缓冲音频帧到1280字节（讯飞要求）再发送
 */
@Slf4j
@Service
public class AsrBridgeService {

    private final XfyunRealtimeClient xfyunClient;
    private final TranscriptMapper transcriptMapper;  // 转写结果入库

    private final AudioWebSocketHandler audioWebSocketHandler;
    private final AudioCacheService audioCacheService;

    // meetingId → speaker counter
    private final Map<String, Integer> speakerCounters = new ConcurrentHashMap<>();
    
    // meetingId → 音频帧缓冲（累积到1280字节再发送）
    private final Map<String, byte[]> pendingAudio = new ConcurrentHashMap<>();
    
    // 讯飞要求的帧大小：16kHz × 16bit × 40ms = 1280字节
    private static final int XFYUN_FRAME_SIZE = 1280;
    private static final int FRAME_INTERVAL_MS = 40;
    
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    @Value("${meeting.asr.primary:xfyun}")
    private String primaryAsr;

    public AsrBridgeService(XfyunRealtimeClient xfyunClient,
                            @Lazy AudioWebSocketHandler audioWebSocketHandler,
                            AudioCacheService audioCacheService,
                            TranscriptMapper transcriptMapper) {
        this.xfyunClient = xfyunClient;
        this.audioWebSocketHandler = audioWebSocketHandler;
        this.audioCacheService = audioCacheService;
        this.transcriptMapper = transcriptMapper;

        // 注册转写结果回调
        xfyunClient.setTranscriptCallback(this::onAsrResult);
    }

    /**
     * 开始实时 ASR（为指定会议建立讯飞连接）
     */
    public boolean startRealtimeAsr(String meetingId) {
        if (!"xfyun".equals(primaryAsr)) {
            log.warn("Primary ASR is not xfyun: {}", primaryAsr);
            return false;
        }

        speakerCounters.put(meetingId, 0);
        pendingAudio.put(meetingId, new byte[0]);
        
        log.info("【ASR启动】meetingId={}", meetingId);

        boolean connected = xfyunClient.connect(meetingId);
        if (!connected) {
            log.error("Failed to connect to Xfyun ASR for meeting: {}", meetingId);
            return false;
        }
        
        // 启动定时发送任务（每40ms发送一帧）
        scheduler.scheduleAtFixedRate(() -> {
            try {
                flushAudioBuffer(meetingId);
            } catch (Exception e) {
                log.warn("Audio buffer flush error: {}", e.getMessage());
            }
        }, FRAME_INTERVAL_MS, FRAME_INTERVAL_MS, TimeUnit.MILLISECONDS);
        
        return true;
    }

    /**
     * 将音频帧转发给 ASR 引擎（先缓冲）
     */
    public void sendAudioFrame(String meetingId, byte[] pcmData) {
        // 缓存音频
        audioCacheService.writeAudioChunk(meetingId, pcmData);

        // 缓冲音频帧
        byte[] pending = pendingAudio.get(meetingId);
        if (pending == null) return;
        
        byte[] newBuffer = new byte[pending.length + pcmData.length];
        System.arraycopy(pending, 0, newBuffer, 0, pending.length);
        System.arraycopy(pcmData, 0, newBuffer, pending.length, pcmData.length);
        pendingAudio.put(meetingId, newBuffer);
    }
    
    /**
     * 刷新音频缓冲（每40ms调用）
     */
    private void flushAudioBuffer(String meetingId) {
        if (!xfyunClient.isConnected() || !meetingId.equals(xfyunClient.getCurrentMeetingId())) {
            return;
        }
        
        byte[] pending = pendingAudio.get(meetingId);
        if (pending == null || pending.length == 0) return;
        
        // 如果缓冲足够，发送一帧
        if (pending.length >= XFYUN_FRAME_SIZE) {
            byte[] frame = new byte[XFYUN_FRAME_SIZE];
            System.arraycopy(pending, 0, frame, 0, XFYUN_FRAME_SIZE);
            
            // 剩余部分保留在缓冲
            byte[] remaining = new byte[pending.length - XFYUN_FRAME_SIZE];
            System.arraycopy(pending, XFYUN_FRAME_SIZE, remaining, 0, remaining.length);
            pendingAudio.put(meetingId, remaining);
            
            // 发送给讯飞
            xfyunClient.sendAudio(frame);
            log.debug("【ASR帧发送】size={} bytes, remaining={}", XFYUN_FRAME_SIZE, remaining.length);
        }
    }

    /**
     * 结束实时 ASR
     */
    public void endRealtimeAsr(String meetingId) {
        log.info("【ASR结束】meetingId={}", meetingId);
        
        // 刷新剩余缓冲
        flushAudioBuffer(meetingId);
        
        xfyunClient.end();
        speakerCounters.remove(meetingId);
        pendingAudio.remove(meetingId);
    }

    /**
     * 断开 ASR 连接
     */
    public void disconnect() {
        scheduler.shutdown();
        xfyunClient.disconnect();
    }

    /**
     * 处理讯飞返回的识别结果 → 推送回前端
     */
    private void onAsrResult(AsrResult result) {
        String meetingId = result.getMeetingId();
        String speaker = "Speaker " + speakerCounters.getOrDefault(meetingId, 0);

        log.info("【ASR转写】meetingId={}, text={}, final={}", 
                meetingId, result.getText(), result.getFinalResult(), result.getIsLast());

        // 推送转写结果到前端 WebSocket
        // 修复：实时转写结果入库（Python版 save_segment）
        try {
            TranscriptSegment segment = new TranscriptSegment();
            segment.setId(UUID.randomUUID().toString());
            segment.setMeetingId(meetingId);
            segment.setSpeakerId(speaker);
            segment.setText(result.getText());
            segment.setStartTimeMs(result.getStartTimeMs() != null ? result.getStartTimeMs() : 0);
            segment.setEndTimeMs(result.getEndTimeMs() != null ? result.getEndTimeMs() : 0);
            segment.setIsFinal(result.getFinalResult());
            segment.setConfidence(result.getConfidence() != null ? result.getConfidence() : 1.0);
            segment.setCorrected(false);
            
            transcriptMapper.insert(segment);
            log.info("【转写入库】segment saved: meetingId={}, text={}", meetingId, result.getText());
        } catch (Exception e) {
            log.warn("Failed to save transcript segment: {}", e.getMessage());
        }
        
        audioWebSocketHandler.sendTranscript(
                meetingId,
                speaker,
                result.getText(),
                result.getFinalResult()
        );
        
        // 如果是最后一条结果，关闭WebSocket
        if (Boolean.TRUE.equals(result.getIsLast())) {
            log.info("【ASR完成】收到最后一条结果，关闭WebSocket: meetingId={}", meetingId);
            audioWebSocketHandler.closeSessionGracefully(meetingId);
        }
    }

    /**
     * 获取当前 ASR 状态
     */
    public boolean isAsrActive() {
        return xfyunClient.isConnected();
    }

    public String getAsrProvider() {
        return primaryAsr;
    }
}
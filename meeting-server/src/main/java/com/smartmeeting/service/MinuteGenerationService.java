package com.smartmeeting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.entity.Participant;
import com.smartmeeting.event.DomainEventPublisher;
import com.smartmeeting.event.MinuteGeneratedEvent;
import com.smartmeeting.enums.MeetingStatus;
import com.smartmeeting.enums.MinuteGenerationStatus;
import com.smartmeeting.repository.MeetingMapper;
import com.smartmeeting.repository.ParticipantMapper;
import com.smartmeeting.config.MeetingMinuteProperties;
import com.smartmeeting.config.MeetingTodoProperties;
import com.smartmeeting.entity.TranscriptSegment;
import com.smartmeeting.repository.TranscriptMapper;
import com.smartmeeting.service.notification.MeetingFeishuNotifier;
import com.smartmeeting.statemachine.MeetingEvent;
import com.smartmeeting.statemachine.MeetingStateMachineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * 纪要生成服务：由 Kafka 消费者或 {@link LocalEventBus} 触发，完成 ASR 校正 → LLM 生成 → 飞书文档写入 → 通知推送。
 * <p>
 * 主要协作组件：{@link OfflineCorrectionService}、{@link VoiceprintService}、{@link TranscriptMapper}、
 * {@link FeishuService}、{@link MeetingFeishuNotifier}、{@link MinuteAIEnhancer}、{@link MeetingMinuteService}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinuteGenerationService {

    private final OfflineCorrectionService correctionService;
    private final VoiceprintService voiceprintService;
    private final MeetingMapper meetingMapper;

    private final ParticipantMapper participantMapper;
    private final TranscriptMapper transcriptMapper;
    private final FeishuService feishuService;
    private final MeetingFeishuNotifier meetingFeishuNotifier;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final MinuteAIEnhancer minuteAIEnhancer;
    private final MeetingMinuteService meetingMinuteService;
    private final MeetingMinuteProperties minuteProperties;
    private final MeetingTodoProperties todoProperties;
    private final MeetingStateMachineService meetingStateMachineService;
    private final DomainEventPublisher domainEventPublisher;
    private final MeetingAudioMaterializerService meetingAudioMaterializerService;
    private final TranscriptSegmentHelper transcriptSegmentHelper;

    @Value("${meeting.llm.api-url:http://localhost}")
    private String llmApiUrl;

    @Value("${meeting.llm.api-key:test}")
    private String llmApiKey;

    @Value("${meeting.llm.model:deepseek-v4-pro}")
    private String llmModel;

    /**
     * 执行完整纪要生成链路：转写校正、LLM 生成、库内持久化、飞书文档创建与通知推送。
     * <p>
     * 会议不存在时静默返回；部分步骤失败时仍尝试标记 COMPLETED 并保存降级内容。
     *
     * @param meetingId 会议 ID
     * @param audioPath 音频文件路径（离线 ASR 降级时使用，可为 null）
     */
    @Transactional
    public void generateMinute(String meetingId, String audioPath) {
        log.info("=== Starting minute generation for meeting: {} ===", meetingId);

        Meeting meeting = meetingMapper.selectById(meetingId);
        if (meeting == null) {
            log.error("Meeting not found: {}", meetingId);
            return;
        }

        String minuteText = "";
        String docToken = "";
        String docUrl = "";

        try {
            // 1. 获取参会人声纹特征ID列表
            LambdaQueryWrapper<Participant> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Participant::getMeetingId, meetingId);
            List<Participant> participants = participantMapper.selectList(wrapper);
            List<String> featureIds = participants.stream()
                    .map(Participant::getFeatureId)
                    .filter(fid -> fid != null && !fid.isEmpty())
                    .toList();

            log.info("Found {} participants, {} with voiceprint features", 
                    participants.size(), featureIds.size());

            // 2. 转写全文：有会中实时定稿分段则优先 DB；否则离线分离+声纹
            log.info("Step 2: Resolving transcript for meeting {}", meetingId);
            String correctedText;
            if (transcriptSegmentHelper.hasFinalRealtimeSegments(meetingId)) {
                correctedText = transcriptSegmentHelper.buildLabeledTranscriptText(
                        transcriptSegmentHelper.listFinalSegments(meetingId));
                log.info("Step 2: Using realtime/final DB segments, length={}", correctedText.length());
            } else {
                String effectiveAudioPath = meetingAudioMaterializerService.materialize(
                        meetingId,
                        audioPath,
                        meeting.getSourceAudioUrl());
                log.info("Step 2: No final segments, offline path audio={}", effectiveAudioPath);
                correctedText = correctionService.correct(meetingId, effectiveAudioPath);
                log.info("Step 2: Offline transcript+voiceprint completed, length={}", correctedText.length());
            }

            // 3. 实时路径下可对仅有 speaker_id 的分段做姓名回写（离线路径已在 correct 内完成）
            if (transcriptSegmentHelper.hasFinalRealtimeSegments(meetingId)) {
                int updated = voiceprintService.updateTranscriptSpeakers(meetingId);
                if (updated > 0) {
                    correctedText = transcriptSegmentHelper.buildLabeledTranscriptText(
                            transcriptSegmentHelper.listFinalSegments(meetingId));
                }
                log.info("Step 3: Realtime speaker name refresh, updated={}", updated);
            }

            // 4. 调用 LLM 生成纪要
            log.info("Step 4: LLM minute generation...");
            minuteText = generateMinuteByLLM(meeting, correctedText, participants);
            log.info("Step 4: LLM generation completed, text length={}", minuteText.length());

            // 🤖 【环节2】AI 纪要增强（可经 meeting.minute.ai-enhancement-enabled 关闭）
            if (minuteProperties.isAiEnhancementEnabled()) {
                try {
                    String participantsNames = participants.stream()
                            .map(Participant::getName)
                            .collect(java.util.stream.Collectors.joining(","));

                    minuteText = minuteAIEnhancer.enhanceMinute(
                            meetingId,
                            minuteText,
                            meeting.getTitle(),
                            meeting.getPresetTypeCode(),
                            participantsNames,
                            correctedText
                    );
                    log.info("Step 4.1: AI minute enhancement completed");
                } catch (Exception e) {
                    log.warn("AI enhancement failed, use original minute: {}", e.getMessage());
                }
            } else {
                log.info("Step 4.1: AI minute enhancement skipped (meeting.minute.ai-enhancement-enabled=false)");
            }

            // 5. 库内持久化（与飞书双写；飞书失败时库内仍可查）
            if (!minuteText.isEmpty()) {
                meetingMinuteService.saveLatest(meetingId, minuteText, MinuteGenerationStatus.READY);
            }

            // 6. 创建飞书文档
            log.info("Step 6: Creating Feishu document...");
            boolean feishuWriteOk = false;
            try {
                Map<String, String> docInfo = feishuService.createDoc("", meeting.getTitle() + " 会议纪要");
                docToken = docInfo.getOrDefault("docToken", "");
                docUrl = docInfo.getOrDefault("docUrl", "");

                if (!docToken.isEmpty() && !minuteText.isEmpty()) {
                    log.info("Step 6.1: Writing minute content to Feishu doc...");
                    feishuWriteOk = feishuService.updateDoc(docToken, minuteText);
                    if (feishuWriteOk) {
                        log.info("Step 6.1: Minute content written to Feishu doc successfully");
                    } else {
                        log.warn("Step 6.1: Failed to write content to Feishu doc (dev mode fallback)");
                    }
                } else {
                    feishuWriteOk = !docToken.isEmpty();
                }
            } catch (Exception e) {
                log.warn("Feishu doc creation failed (dev mode): {}", e.getMessage());
                docUrl = String.format("http://localhost:8765/meetings/%s/minute", meetingId);
            }

            if (!minuteText.isEmpty() && !feishuWriteOk) {
                meetingMinuteService.saveLatest(meetingId, minuteText, MinuteGenerationStatus.PARTIAL);
            }

            // 7. 更新会议记录 → COMPLETED
            meetingStateMachineService.apply(meetingId, MeetingEvent.MINUTE_READY);
            meeting.setDocToken(docToken);
            meeting.setDocUrl(docUrl);
            meeting.setStatus(MeetingStatus.COMPLETED.name());
            meetingMapper.updateById(meeting);
            if (!minuteText.isEmpty()) {
                meetingMinuteService.updateContentUrl(meetingId, docUrl);
            }
            if (todoProperties.isExtractionEnabled()) {
                domainEventPublisher.publish(new MinuteGeneratedEvent(meetingId, System.currentTimeMillis()));
            } else {
                log.info("Todo extraction skipped by config (meeting.todo.extraction-enabled=false): meetingId={}", meetingId);
            }

            log.info("Step 7: Meeting status updated to COMPLETED, docUrl={}", docUrl);
            log.info("=== Minute generation completed for meeting: {} ===", meetingId);

            // 7. 推送飞书卡片通知
            try {
                pushFeishuCard(meeting, docUrl, minuteText);
            } catch (Exception e) {
                log.debug("Feishu notification skipped (dev mode): {}", e.getMessage());
            }

        } catch (Exception e) {
            log.error("Minute generation failed for meeting: {}", meetingId, e);
            if (minuteText != null && !minuteText.isEmpty()) {
                meetingMinuteService.saveLatest(meetingId, minuteText, MinuteGenerationStatus.FAILED);
            }
            if (meeting.getStatus() == null || !meeting.getStatus().equals(MeetingStatus.COMPLETED.name())) {
                String fallbackUrl = String.format("http://localhost:8765/meetings/%s/minute", meetingId);
                meeting.setDocUrl(fallbackUrl);
                meetingStateMachineService.forceStatus(meetingId, MeetingStatus.COMPLETED);
                meeting.setStatus(MeetingStatus.COMPLETED.name());
                meetingMapper.updateById(meeting);
                meetingMinuteService.updateContentUrl(meetingId, fallbackUrl);
                log.info("Meeting marked as COMPLETED with fallback content");
            }
        }
    }

    /**
     * 推送「纪要已生成」飞书卡片或文本消息（群聊优先，无 chatId 时降级单聊创建人）。
     *
     * @param meeting    会议实体
     * @param docUrl     飞书文档 URL（可为空，将从 docToken 构造）
     * @param minuteText 纪要正文（当前仅用于日志，卡片展示文档链接）
     */
    private void pushFeishuCard(Meeting meeting, String docUrl, String minuteText) {
        // 构建卡片元素
        java.util.List<Map<String, String>> elements = new java.util.ArrayList<>();
        
        // 修复：如果docUrl为空，用docToken构造
        if (docUrl == null || docUrl.isEmpty()) {
            String docToken = meeting.getDocToken();
            if (docToken != null && !docToken.isEmpty()) {
                docUrl = "https://bytedance.feishu.cn/docx/" + docToken;
            }
        }
        elements.add(Map.of(
                "content", String.format("**会议纪要已生成**\n\n📋 会议: %s\n🏢 公司: %s",
                        meeting.getTitle(), meeting.getCompany())
        ));
        
        if (!docUrl.isEmpty()) {
            elements.add(Map.of(
                    "content", String.format("\n📄 [点击查看纪要文档](%s)", docUrl),
                    "doc_url", docUrl
            ));
        }

        // 发送卡片消息
        String chatId = meeting.getChatId() != null ? meeting.getChatId() : "";
        String idempotencyKey = "minute-ready:" + meeting.getId();
        if (!chatId.isEmpty()) {
            meetingFeishuNotifier.sendCardMessage(
                    chatId, "会议纪要生成通知", elements,
                    "MINUTE_READY", meeting.getId(), idempotencyKey);
        } else {
            String text = String.format("✅ 会议纪要已生成\n\n会议: %s\n公司: %s\n文档: %s",
                    meeting.getTitle(), meeting.getCompany(), docUrl);
            meetingFeishuNotifier.sendTextMessage(
                    meeting.getCreatorId(), text,
                    "MINUTE_READY", meeting.getId(), idempotencyKey + ":text");
        }
    }

    /**
     * 调用 LLM API 根据转写文本生成结构化纪要；失败时降级为简易纪要。
     *
     * @param meeting        会议实体
     * @param transcriptText 校正后的转写全文
     * @param participants   参会人列表
     * @return 纪要 Markdown 文本
     */
    private String generateMinuteByLLM(Meeting meeting, String transcriptText, List<Participant> participants) {
        // 构建 Prompt
        String prompt = buildMinutePrompt(meeting, transcriptText, participants);

        // 调用 LLM API
        String url = llmApiUrl + "/v1/chat/completions";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(llmApiKey);

        Map<String, Object> body = Map.of(
                "model", llmModel,
                "messages", List.of(
                        Map.of("role", "system", "content", "你是专业的会议纪要助手，请根据会议录音转写文本生成结构化纪要。"),
                        Map.of("role", "user", "content", prompt)
                ),
                "temperature", 0.3,
                "max_tokens", 4000
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.POST, request, JsonNode.class);
            JsonNode json = response.getBody();
            
            if (json != null && json.has("choices")) {
                return json.get("choices").get(0).path("message").path("content").asText();
            }
            
            log.warn("LLM returned unexpected response");
            return generateSimpleMinute(meeting, transcriptText);
            
        } catch (Exception e) {
            log.warn("LLM call failed: {} - using simple minute", e.getMessage());
            return generateSimpleMinute(meeting, transcriptText);
        }
    }

    /**
     * 构建 LLM 纪要生成 Prompt，包含会议元信息与转写文本。
     *
     * @param meeting        会议实体
     * @param transcriptText 转写全文
     * @param participants   参会人列表
     * @return 完整 Prompt 字符串
     */
    private String buildMinutePrompt(Meeting meeting, String transcriptText, List<Participant> participants) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请根据以下会议录音转写文本，生成结构化会议纪要。\n\n");
        prompt.append("## 会议信息\n");
        prompt.append("- 会议主题: ").append(meeting.getTitle()).append("\n");
        prompt.append("- 公司: ").append(meeting.getCompany()).append("\n");
        if (meeting.getDepartment() != null) {
            prompt.append("- 部门: ").append(meeting.getDepartment()).append("\n");
        }
        prompt.append("- 会议组: ").append(meeting.getGroupName()).append("\n");
        prompt.append("- 参会人: ");
        participants.forEach(p -> prompt.append(p.getName()).append(", "));
        prompt.append("\n\n");
        prompt.append("## 会议转写文本\n");
        prompt.append(transcriptText).append("\n\n");
        prompt.append("## 输出要求\n");
        prompt.append("请按以下格式输出：\n");
        prompt.append("1. 会议概述（200字以内）\n");
        prompt.append("2. 主要议题及讨论内容\n");
        prompt.append("3. 决议事项\n");
        prompt.append("4. 待办事项（责任人、截止时间）\n");
        prompt.append("5. 下次会议建议\n");
        
        return prompt.toString();
    }

    /**
     * LLM 调用失败时的降级方案：生成包含会议信息与转写摘要的简易纪要。
     *
     * @param meeting        会议实体
     * @param transcriptText 转写全文
     * @return 简易纪要 Markdown 文本
     */
    private String generateSimpleMinute(Meeting meeting, String transcriptText) {
        StringBuilder minute = new StringBuilder();
        minute.append("# 会议纪要\n\n");
        minute.append("## 会议信息\n");
        minute.append("- 主题: ").append(meeting.getTitle()).append("\n");
        minute.append("- 公司: ").append(meeting.getCompany()).append("\n");
        minute.append("\n## 转写内容\n");
        if (transcriptText != null && !transcriptText.isEmpty()) {
            minute.append(transcriptText.substring(0, Math.min(1000, transcriptText.length())));
            if (transcriptText.length() > 1000) {
                minute.append("...");
            }
        } else {
            minute.append("*（音频文件为空或ASR未返回结果）*\n");
        }
        minute.append("\n\n---\n*注：此为自动生成的简易纪要，请人工审核补充。*");
        
        return minute.toString();
    }
}

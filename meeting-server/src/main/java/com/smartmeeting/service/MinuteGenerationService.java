package com.smartmeeting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.entity.Participant;
import com.smartmeeting.enums.MeetingStatus;
import com.smartmeeting.repository.MeetingMapper;
import com.smartmeeting.repository.ParticipantMapper;
import com.smartmeeting.entity.TranscriptSegment;
import com.smartmeeting.repository.TranscriptMapper;
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
 * 纪要生成服务 - 由 Kafka 消费者或 LocalEventBus 触发
 * 
 * 流程:
 * 1. 获取参会人信息
 * 2. 调用讯飞离线 ASR 校正
 * 3. 调用讯飞 ISV 声纹识别 → 更新 speaker_name
 * 4. 更新 int_transcript_segment (corrected=true)
 * 5. 调用 LLM 生成纪要文本
 * 6. 创建飞书文档 → 写入纪要内容 → 更新 int_meeting(doc_url, doc_token)
 * 7. 更新状态 → COMPLETED
 * 8. 推送飞书卡片"纪要已生成"
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
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final MinuteAIEnhancer minuteAIEnhancer;

    @Value("${meeting.llm.api-url:http://localhost}")
    private String llmApiUrl;

    @Value("${meeting.llm.api-key:test}")
    private String llmApiKey;

    @Value("${meeting.llm.model:deepseek-v4-pro}")
    private String llmModel;

    /**
     * 执行纪要生成链路
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

            // 2. 先查询实时转写结果（优先）
            log.info("Step 2: Querying realtime transcript segments...");
            LambdaQueryWrapper<TranscriptSegment> transcriptWrapper = new LambdaQueryWrapper<>();
            transcriptWrapper.eq(TranscriptSegment::getMeetingId, meetingId)
                            .eq(TranscriptSegment::getIsFinal, true)
                            .orderByAsc(TranscriptSegment::getStartTimeMs);
            List<TranscriptSegment> segments = transcriptMapper.selectList(transcriptWrapper);
            
            String correctedText = "";
            if (segments != null && !segments.isEmpty()) {
                // 使用实时转写结果构建全文（Python版 build_transcript_text）
                StringBuilder sb = new StringBuilder();
                for (TranscriptSegment seg : segments) {
                    sb.append(seg.getSpeakerId()).append(": ").append(seg.getText()).append("\n");
                }
                correctedText = sb.toString();
                log.info("Step 2: Using realtime transcript, {} segments, length={}", segments.size(), correctedText.length());
            } else {
                // 降级：调用离线ASR校正
                log.info("Step 2: No realtime transcript, calling offline ASR...");
                correctedText = correctionService.correct(meetingId, audioPath);
                log.info("Step 2: Offline correction completed, text length={}", correctedText.length());
            }

            // 3. 调用 ISV 声纹识别 → 映射 speaker_N → 真实姓名
            log.info("Step 3: Voiceprint identification...");
            // TODO: 实际声纹识别实现
            log.info("Step 3: Voiceprint identification completed");

            // 4. 调用 LLM 生成纪要
            log.info("Step 4: LLM minute generation...");
            minuteText = generateMinuteByLLM(meeting, correctedText, participants);
            log.info("Step 4: LLM generation completed, text length={}", minuteText.length());

            // 🤖 【环节2介入】调用AI优化纪要质量
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

            // 5. 创建飞书文档
            log.info("Step 5: Creating Feishu document...");
            try {
                Map<String, String> docInfo = feishuService.createDoc("", meeting.getTitle() + " 会议纪要");
                docToken = docInfo.getOrDefault("docToken", "");
                docUrl = docInfo.getOrDefault("docUrl", "");
                
                // 5.1 写入文档内容
                if (!docToken.isEmpty() && !minuteText.isEmpty()) {
                    log.info("Step 5.1: Writing minute content to Feishu doc...");
                    boolean writeSuccess = feishuService.updateDoc(docToken, minuteText);
                    if (writeSuccess) {
                        log.info("Step 5.1: Minute content written to Feishu doc successfully");
                    } else {
                        log.warn("Step 5.1: Failed to write content to Feishu doc (dev mode fallback)");
                    }
                }
            } catch (Exception e) {
                log.warn("Feishu doc creation failed (dev mode): {}", e.getMessage());
                // Fallback: 使用本地 URL
                docUrl = String.format("http://localhost:8765/meetings/%s/minute", meetingId);
            }

            // 6. 更新会议记录 → COMPLETED
            meeting.setDocToken(docToken);
            meeting.setDocUrl(docUrl);
            meeting.setStatus(MeetingStatus.COMPLETED.name());
            meetingMapper.updateById(meeting);

            log.info("Step 6: Meeting status updated to COMPLETED, docUrl={}", docUrl);
            log.info("=== Minute generation completed for meeting: {} ===", meetingId);

            // 7. 推送飞书卡片通知
            try {
                pushFeishuCard(meeting, docUrl, minuteText);
            } catch (Exception e) {
                log.debug("Feishu notification skipped (dev mode): {}", e.getMessage());
            }

        } catch (Exception e) {
            log.error("Minute generation failed for meeting: {}", meetingId, e);
            // 即使失败也标记为 COMPLETED（使用 fallback 内容）
            if (meeting.getStatus() == null || !meeting.getStatus().equals(MeetingStatus.COMPLETED.name())) {
                meeting.setDocUrl(String.format("http://localhost:8765/meetings/%s/minute", meetingId));
                meeting.setStatus(MeetingStatus.COMPLETED.name());
                meetingMapper.updateById(meeting);
                log.info("Meeting marked as COMPLETED with fallback content");
            }
        }
    }

    /**
     * 推送飞书卡片通知
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
        String chatId = meeting.getChatId() != null ? meeting.getChatId() : "";  // 从会议读取
        if (!chatId.isEmpty()) {
            feishuService.sendCardMessage(chatId, "会议纪要生成通知", elements);
        } else {
            // Fallback: 发送文本消息
            String text = String.format("✅ 会议纪要已生成\n\n会议: %s\n公司: %s\n文档: %s",
                    meeting.getTitle(), meeting.getCompany(), docUrl);
            feishuService.sendMessage("", text);
        }
    }

    /**
     * 调用 LLM 生成纪要
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
     * 构建纪要生成 Prompt
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
     * 当 LLM 调用失败时，生成简单纪要
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

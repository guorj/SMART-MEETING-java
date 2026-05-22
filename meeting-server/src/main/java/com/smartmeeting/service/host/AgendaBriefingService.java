package com.smartmeeting.service.host;

import com.smartmeeting.entity.Meeting;
import com.smartmeeting.repository.MeetingMapper;
import com.smartmeeting.service.AiAgentService;
import com.smartmeeting.service.BitableDirectiveBuilder;
import com.smartmeeting.service.feishu.FeishuResourceResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 主持会序 OpenClaw 通报：仅调用 OpenClaw Gateway，无飞书+LLM 兜底。
 * 是否触发由主持层根据 {@code int_matter_progress_doc_config}（openclaw_briefing + feishu_doc_url）决定。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgendaBriefingService {

    private final MeetingMapper meetingMapper;
    private final AiAgentService aiAgentService;
    private final BitableDirectiveBuilder bitableDirectiveBuilder;

    @Value("${meeting.host.agenda-briefing.enabled:true}")
    private boolean enabled;

    /**
     * 为指定会序生成通报正文（仅 OpenClaw）。
     *
     * @param meetingId   会议 ID
     * @param agendaTitle 会序标题
     * @param feishuUrl   配置表 {@code feishu_doc_url}（须可识别）
     * @param feishuKind  BASE / DOCX / WIKI 等
     */
    public AgendaBriefingResult generateBriefing(String meetingId,
                                                 String agendaTitle,
                                                 String feishuUrl,
                                                 String feishuKind) {
        if (!enabled) {
            return AgendaBriefingResult.builder()
                    .source("disabled")
                    .errorMessage("会序 OpenClaw 通报未启用（meeting.host.agenda-briefing.enabled=false）")
                    .build();
        }
        Meeting meeting = meetingMapper.selectById(meetingId);
        if (meeting == null) {
            return AgendaBriefingResult.builder()
                    .source("error")
                    .errorMessage("会议不存在: " + meetingId)
                    .build();
        }
        String url = feishuUrl != null ? feishuUrl.trim() : "";
        if (url.isEmpty() || !FeishuResourceResolver.isRecognizedFeishuDocUrl(url)) {
            return AgendaBriefingResult.builder()
                    .source("error")
                    .errorMessage("配置表 feishu_doc_url 无效或未配置")
                    .build();
        }

        String directive = bitableDirectiveBuilder.buildDirectiveForHostAgenda(agendaTitle, url, feishuKind);
        if (directive == null || directive.isBlank()) {
            return AgendaBriefingResult.builder()
                    .source("error")
                    .errorMessage("无法构建会序通报指令")
                    .build();
        }

        if (!aiAgentService.isAgentAvailable()) {
            return AgendaBriefingResult.builder()
                    .source("error")
                    .errorMessage("OpenClaw Gateway 不可用")
                    .build();
        }
        String openclawMd = aiAgentService.runAgendaBriefingViaOpenclaw(
                meeting, directive, url, agendaTitle != null ? agendaTitle : "");
        if (openclawMd != null && !openclawMd.isBlank()) {
            return AgendaBriefingResult.builder()
                    .markdown(openclawMd.trim())
                    .source("openclaw_gateway")
                    .build();
        }
        log.warn("OpenClaw agenda briefing empty meetingId={} title={}", meetingId, agendaTitle);
        return AgendaBriefingResult.builder()
                .source("error")
                .errorMessage("OpenClaw 返回为空")
                .build();
    }
}

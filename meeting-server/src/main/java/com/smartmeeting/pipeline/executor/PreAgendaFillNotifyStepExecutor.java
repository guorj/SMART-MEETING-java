package com.smartmeeting.pipeline.executor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.pipeline.StepExecutionContext;
import com.smartmeeting.pipeline.StepExecutionResult;
import com.smartmeeting.pipeline.StepExecutor;
import com.smartmeeting.service.FeishuService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class PreAgendaFillNotifyStepExecutor implements StepExecutor {

    private final FeishuService feishuService;
    private final PipelineExecutorSupport support;

    @Override
    public String stepType() {
        return "pre-agenda-fill-notify";
    }

    @Override
    public StepExecutionResult execute(StepExecutionContext context) {
        Meeting meeting = context.getMeeting();
        JsonNode cfg = support.parseConfig(context.getStep().getConfigJson());
        JsonNode shared = support.parseConfig(context.getSharedContextJson());
        JsonNode tokenMap = shared.path("agendaFillTokenByUser");
        String campaignId = shared.path("agendaFillCampaignId").asText("");
        String entryUrl = support.text(cfg, "entryUrl", "/meeting-server/agenda-fill.html");
        String userTemplate = support.text(cfg, "userMessageTemplate",
                "请在会前完成你负责会序资料回填：{fillUrl}");
        String groupTemplate = support.text(cfg, "groupMessageTemplate",
                "会前会序资料回填已发起，请相关同事打开个人通知中的回填链接完成提交。");
        int sentUsers = 0;
        int sentGroups = 0;

        // 1) 个人发送（默认开启）
        boolean sendParticipantUsers = support.bool(cfg, "sendParticipantUsers", true);
        if (sendParticipantUsers && tokenMap.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = tokenMap.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> e = fields.next();
                String userId = e.getKey();
                String token = e.getValue().asText("");
                if (userId == null || userId.isBlank() || token == null || token.isBlank()) {
                    continue;
                }
                String fillUrl = withToken(entryUrl, token);
                String text = userTemplate.replace("{fillUrl}", fillUrl)
                        .replace("{meetingTitle}", meeting != null && meeting.getTitle() != null ? meeting.getTitle() : "")
                        .replace("{meetingId}", context.getMeetingId());
                if (feishuService.sendMessageToUserId(userId, text)) {
                    sentUsers++;
                }
            }
        }

        // 2) 群发送（兼容仅群 / 混合）
        List<String> groupIds = parseStringList(cfg.path("groupIds"));
        String cfgChatId = support.text(cfg, "groupId", "");
        if (!cfgChatId.isBlank()) {
            groupIds.add(cfgChatId);
        }
        if (groupIds.isEmpty() && meeting != null && meeting.getChatId() != null && !meeting.getChatId().isBlank()
                && support.bool(cfg, "fallbackMeetingChat", true)) {
            groupIds.add(meeting.getChatId());
        }
        boolean groupClaimCard = support.bool(cfg, "groupClaimCard", true) && !campaignId.isBlank();
        boolean groupInlineFillCard = support.bool(cfg, "groupInlineFillCard", false) && !campaignId.isBlank();
        String groupTokenUserId = support.text(cfg, "groupTokenUserId", "");
        String tokenForGroup = groupTokenUserId.isBlank() ? "" : tokenMap.path(groupTokenUserId).asText("");
        String groupTitle = support.text(cfg, "groupCardTitle", "会序资料回填");
        String groupInlineTitle = support.text(cfg, "groupInlineCardTitle", "会序资料回填（群内直填）");
        String groupText = groupTemplate;
        if (!tokenForGroup.isBlank()) {
            groupText = groupText + "\n填报入口：" + withToken(entryUrl, tokenForGroup);
        }
        String claimCard = buildClaimCardJson(groupTitle, groupTemplate, campaignId, entryUrl);
        String inlineCard = buildInlineFillCardJson(groupInlineTitle, groupTemplate, campaignId);
        for (String gid : groupIds) {
            if (gid == null || gid.isBlank()) {
                continue;
            }
            boolean ok;
            if (groupInlineFillCard) {
                ok = feishuService.sendInteractiveCard(gid, inlineCard);
            } else if (groupClaimCard) {
                ok = feishuService.sendInteractiveCard(gid, claimCard);
            } else {
                ok = feishuService.sendMessage(gid, groupText);
            }
            if (ok) {
                sentGroups++;
            }
        }

        // 3) 额外个人（可配置）
        for (String extraUser : parseStringList(cfg.path("extraUserIds"))) {
            if (extraUser == null || extraUser.isBlank()) {
                continue;
            }
            String text = "会前会序资料回填已发起。";
            if (feishuService.sendMessageToUserId(extraUser, text)) {
                sentUsers++;
            }
        }

        return StepExecutionResult.ok("agenda-fill-notify-ok",
                "{\"sentUsers\":" + sentUsers + ",\"sentGroups\":" + sentGroups + "}");
    }

    private String withToken(String entryUrl, String token) {
        String sep = entryUrl.contains("?") ? "&" : "?";
        return entryUrl + sep + "token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    private List<String> parseStringList(JsonNode node) {
        List<String> out = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return out;
        }
        for (JsonNode n : node) {
            String s = n.asText("").trim();
            if (!s.isBlank()) {
                out.add(s);
            }
        }
        return out;
    }

    private String buildClaimCardJson(String title, String body, String campaignId, String entryUrl) {
        ObjectNode card = support.newObject();
        card.putObject("config").put("wide_screen_mode", true);
        ObjectNode header = card.putObject("header");
        header.put("template", "blue");
        header.putObject("title").put("tag", "plain_text").put("content", title);
        ArrayNode elements = card.putArray("elements");
        elements.addObject().put("tag", "div")
                .putObject("text").put("tag", "lark_md").put("content", body);
        ObjectNode action = elements.addObject();
        action.put("tag", "action");
        ObjectNode btn = action.putArray("actions").addObject();
        btn.put("tag", "button");
        btn.put("type", "primary");
        btn.putObject("text").put("tag", "plain_text").put("content", "领取我的回填链接");
        ObjectNode value = btn.putObject("value");
        value.put("cmd", "agenda-fill-claim");
        value.put("campaignId", campaignId);
        value.put("entryUrl", entryUrl);
        return card.toString();
    }

    private String buildInlineFillCardJson(String title, String body, String campaignId) {
        ObjectNode card = support.newObject();
        card.putObject("config").put("wide_screen_mode", true);
        ObjectNode header = card.putObject("header");
        header.put("template", "blue");
        header.putObject("title").put("tag", "plain_text").put("content", title);
        ArrayNode elements = card.putArray("elements");
        elements.addObject().put("tag", "div")
                .putObject("text").put("tag", "lark_md")
                .put("content", body + "\n请填写后点击提交（会序编号从 0 开始）。");

        ObjectNode idxInput = elements.addObject();
        idxInput.put("tag", "input");
        idxInput.put("name", "agendaIndex");
        idxInput.putObject("label").put("tag", "plain_text").put("content", "会序编号");
        idxInput.putObject("placeholder").put("tag", "plain_text").put("content", "例如 0");

        ObjectNode minutesInput = elements.addObject();
        minutesInput.put("tag", "input");
        minutesInput.put("name", "minutes");
        minutesInput.putObject("label").put("tag", "plain_text").put("content", "时长(分钟)");
        minutesInput.putObject("placeholder").put("tag", "plain_text").put("content", "例如 15");

        ObjectNode urlInput = elements.addObject();
        urlInput.put("tag", "input");
        urlInput.put("name", "url");
        urlInput.putObject("label").put("tag", "plain_text").put("content", "资料链接 URL");
        urlInput.putObject("placeholder").put("tag", "plain_text").put("content", "https://...");

        ObjectNode action = elements.addObject();
        action.put("tag", "action");
        ObjectNode btn = action.putArray("actions").addObject();
        btn.put("tag", "button");
        btn.put("type", "primary");
        btn.putObject("text").put("tag", "plain_text").put("content", "提交回填");
        ObjectNode value = btn.putObject("value");
        value.put("cmd", "agenda-fill-inline-submit");
        value.put("campaignId", campaignId);
        return card.toString();
    }
}


package com.smartmeeting.pipeline.executor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.pipeline.StepExecutionContext;
import com.smartmeeting.pipeline.StepExecutionResult;
import com.smartmeeting.pipeline.StepExecutor;
import com.smartmeeting.service.AgendaFillCampaignService;
import com.smartmeeting.service.FeishuService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 单独发送 leader 会序确认通知，和 owner 通知步骤解耦。
 */
@Component
@RequiredArgsConstructor
public class PreAgendaLeaderNotifyStepExecutor implements StepExecutor {

    private final FeishuService feishuService;
    private final AgendaFillCampaignService agendaFillCampaignService;
    private final PipelineExecutorSupport support;

    @Override
    public String stepType() {
        return "pre-agenda-leader-notify";
    }

    @Override
    public StepExecutionResult execute(StepExecutionContext context) {
        JsonNode cfg = support.parseConfig(context.getStep().getConfigJson());
        JsonNode shared = support.parseConfig(context.getSharedContextJson());
        String campaignId = support.text(cfg, "campaignId", shared.path("agendaFillCampaignId").asText(""));
        if (campaignId == null || campaignId.isBlank()) {
            return StepExecutionResult.ok("agenda-leader-notify-skip", "{\"sent\":0,\"reason\":\"campaign_id_missing\"}");
        }

        List<String> fromCfg = parseStringList(cfg.path("leaderUserIds"));
        List<String> fromShared = parseStringList(shared.path("agendaFillLeaderUserIds"));
        String resolvedLeaderUserId = trim(shared.path("leaderResolvedUserId").asText(""));
        Set<String> leaderUserIds = new LinkedHashSet<>();
        leaderUserIds.addAll(fromCfg);
        leaderUserIds.addAll(fromShared);
        if (resolvedLeaderUserId != null) {
            leaderUserIds.add(resolvedLeaderUserId);
        }
        if (leaderUserIds.isEmpty()) {
            return StepExecutionResult.ok("agenda-leader-notify-skip", "{\"sent\":0,\"reason\":\"leader_user_ids_empty\"}");
        }

        String entryUrl = support.text(cfg, "entryUrl", "/meeting-server/agenda-fill.html");
        String messageTemplate = support.text(cfg, "leaderMessageTemplate",
                "请作为负责人确认会序信息：{fillUrl}\n会议：{meetingTitle}");
        Meeting meeting = context.getMeeting();
        String meetingTitle = meeting != null && meeting.getTitle() != null ? meeting.getTitle() : "";
        String meetingId = context.getMeetingId() == null ? "" : context.getMeetingId();
        String presetName = shared.path("agendaFillPresetName").asText(meetingTitle);

        int sent = 0;
        ArrayNode failedUsers = support.newObject().putArray("failedUsers");
        for (String userId : leaderUserIds) {
            try {
                String token = agendaFillCampaignService.claimTokenForUser(campaignId, userId);
                String fillUrl = withToken(entryUrl, token);
                String text = messageTemplate
                        .replace("{fillUrl}", fillUrl)
                        .replace("{meetingTitle}", meetingTitle)
                        .replace("{meetingId}", meetingId)
                        .replace("{presetName}", presetName)
                        .replace("{campaignId}", campaignId);
                if (feishuService.sendMessageToUserId(userId, text)) {
                    sent++;
                } else {
                    failedUsers.add(userId);
                }
            } catch (Exception ex) {
                failedUsers.add(userId);
            }
        }

        ObjectNode out = support.newObject();
        out.put("agendaLeaderSent", sent);
        out.put("agendaLeaderTotal", leaderUserIds.size());
        out.put("agendaLeaderCampaignId", campaignId);
        out.set("agendaLeaderFailedUsers", failedUsers);
        return StepExecutionResult.ok("agenda-leader-notify-ok", out.toString());
    }

    private String withToken(String entryUrl, String token) {
        String sep = entryUrl.contains("?") ? "&" : "?";
        return entryUrl + sep + "token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    private List<String> parseStringList(JsonNode n) {
        List<String> out = new ArrayList<>();
        if (n == null || !n.isArray()) {
            return out;
        }
        for (JsonNode x : n) {
            String s = trim(x.asText(""));
            if (s != null) {
                out.add(s);
            }
        }
        return out;
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        String out = value.trim();
        return out.isEmpty() ? null : out;
    }
}

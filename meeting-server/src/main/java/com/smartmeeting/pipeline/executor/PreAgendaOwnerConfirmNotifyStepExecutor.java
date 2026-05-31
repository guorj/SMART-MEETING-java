package com.smartmeeting.pipeline.executor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartmeeting.entity.MeetingTypePreset;
import com.smartmeeting.entity.PipelineStep;
import com.smartmeeting.pipeline.StepExecutionContext;
import com.smartmeeting.pipeline.StepExecutionResult;
import com.smartmeeting.pipeline.StepExecutor;
import com.smartmeeting.service.AgendaFillCampaignService;
import com.smartmeeting.service.FeishuService;
import com.smartmeeting.service.ParticipantService;
import com.smartmeeting.repository.MeetingTypePresetMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 按会序 owners 定向发送“会序确认+可编辑入口”通知。
 */
@Component
@RequiredArgsConstructor
public class PreAgendaOwnerConfirmNotifyStepExecutor implements StepExecutor {

    private final AgendaFillCampaignService agendaFillCampaignService;
    private final FeishuService feishuService;
    private final MeetingTypePresetMapper meetingTypePresetMapper;
    private final ParticipantService participantService;
    private final PipelineExecutorSupport support;

    @Override
    public String stepType() {
        return "pre-agenda-owner-confirm-notify";
    }

    @Override
    public StepExecutionResult execute(StepExecutionContext context) {
        JsonNode cfg = support.parseConfig(context.getStep().getConfigJson());
        JsonNode shared = support.parseConfig(context.getSharedContextJson());
        boolean meetingMode = context.getMeeting() != null && context.getMeetingId() != null && !context.getMeetingId().isBlank();
        Integer presetCode = resolvePresetCode(context, cfg, shared);
        if (presetCode == null || presetCode <= 0) {
            return StepExecutionResult.failed("presetTypeCode missing");
        }
        AgendaFillCampaignService.CampaignCreateRequest req = new AgendaFillCampaignService.CampaignCreateRequest();
        if (meetingMode) {
            req.setMeetingId(context.getMeetingId());
        } else {
            req.setPresetCode(presetCode);
        }
        req.setExpireMinutes(support.number(cfg, "expireMinutes", 180));
        List<String> leaderUserIds = parseStringList(cfg.path("leaderUserIds"));
        LeaderResolveInfo leaderResolve = meetingMode
                ? resolveLeaderFromPreset(context)
                : resolveLeaderFromPresetDirect(presetCode);
        if ("resolved".equals(leaderResolve.status) && leaderResolve.userId != null
                && leaderUserIds.stream().noneMatch(leaderResolve.userId::equals)) {
            leaderUserIds.add(leaderResolve.userId);
        }
        boolean leaderResolveAlertSent = meetingMode && notifyLeaderResolveIssueIfNeeded(context, cfg, leaderResolve);
        req.setLeaderUserIds(leaderUserIds);
        Set<String> leaderUserIdSet = new LinkedHashSet<>(leaderUserIds);
        Map<String, List<Integer>> participantAgenda = parseParticipantAgenda(cfg.path("participantAgenda"));
        if (participantAgenda.isEmpty()) {
            participantAgenda = meetingMode
                    ? agendaFillCampaignService.resolveParticipantAgendaFromMeeting(context.getMeetingId())
                    : agendaFillCampaignService.resolveParticipantAgendaFromPreset(presetCode);
        }
        req.setParticipantAgenda(participantAgenda);
        AgendaFillCampaignService.CampaignCreated created = agendaFillCampaignService.createCampaign(req);

        String entryUrl = support.text(cfg, "entryUrl", "/meeting-server/agenda-fill.html");
        String userTemplate = support.text(cfg, "userMessageTemplate",
                "请确认并完善你负责的会序：{fillUrl}\n会议：{meetingTitle}");
        String meetingTitle = meetingMode
                ? (context.getMeeting().getTitle() == null ? "" : context.getMeeting().getTitle())
                : resolvePresetName(presetCode);
        String meetingIdText = meetingMode ? context.getMeetingId() : "";
        int sentUsers = 0;
        ArrayNode failedUsers = support.newObject().putArray("failedUsers");
        if (created.getTokenByUser() != null) {
            for (Map.Entry<String, String> e : created.getTokenByUser().entrySet()) {
                String userId = e.getKey();
                String token = e.getValue();
                if (userId == null || userId.isBlank() || token == null || token.isBlank()) {
                    continue;
                }
                if (leaderUserIdSet.contains(userId)) {
                    // leader 通知拆分到 pre-agenda-leader-notify，当前步骤仅发送 owners
                    continue;
                }
                String fillUrl = withToken(entryUrl, token);
                String text = userTemplate
                        .replace("{fillUrl}", fillUrl)
                        .replace("{meetingTitle}", meetingTitle)
                        .replace("{meetingId}", meetingIdText)
                        .replace("{presetTypeCode}", String.valueOf(presetCode))
                        .replace("{presetName}", meetingTitle);
                boolean ok = feishuService.sendMessageToUserId(userId, text);
                if (ok) {
                    sentUsers++;
                } else {
                    failedUsers.add(userId);
                }
            }
        }

        ObjectNode out = support.newObject();
        out.put("agendaFillCampaignId", created.getCampaignId());
        out.put("agendaFillMeetingId", meetingMode ? context.getMeetingId() : "");
        out.put("agendaFillPresetCode", presetCode);
        out.put("agendaFillPresetMode", !meetingMode);
        int ownerTotal = created.getTokenByUser() == null ? 0
                : (int) created.getTokenByUser().keySet().stream().filter(uid -> !leaderUserIdSet.contains(uid)).count();
        out.put("agendaOwnerTotal", ownerTotal);
        out.put("agendaOwnerSent", sentUsers);
        out.put("leaderNameFromPreset", leaderResolve.leaderName == null ? "" : leaderResolve.leaderName);
        out.put("leaderResolvedUserId", leaderResolve.userId == null ? "" : leaderResolve.userId);
        out.put("leaderResolveStatus", leaderResolve.status);
        out.put("leaderResolveAlertSent", leaderResolveAlertSent);
        ArrayNode leaderUserIdsOut = out.putArray("agendaFillLeaderUserIds");
        for (String uid : leaderUserIds) {
            leaderUserIdsOut.add(uid);
        }
        out.set("failedUsers", failedUsers);
        return StepExecutionResult.ok("agenda-owner-confirm-notify-ok", out.toString());
    }

    public StepExecutionResult executeForPreset(int presetTypeCode, PipelineStep step, String stage, String sharedContextJson) {
        String mergedShared = mergePresetCodeIntoShared(sharedContextJson, presetTypeCode);
        StepExecutionContext context = StepExecutionContext.builder()
                .meetingId("PRESET_" + presetTypeCode)
                .stage(stage == null || stage.isBlank() ? "PRE" : stage)
                .step(step)
                .meeting(null)
                .sharedContextJson(mergedShared)
                .build();
        return execute(context);
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
            String s = x.asText("").trim();
            if (!s.isBlank()) {
                out.add(s);
            }
        }
        return out;
    }

    private Map<String, List<Integer>> parseParticipantAgenda(JsonNode n) {
        if (n == null || !n.isObject()) {
            return Map.of();
        }
        ObjectNode obj = (ObjectNode) n;
        java.util.LinkedHashMap<String, List<Integer>> out = new java.util.LinkedHashMap<>();
        obj.fieldNames().forEachRemaining(uid -> {
            JsonNode arr = obj.path(uid);
            List<Integer> idx = new ArrayList<>();
            if (arr.isArray()) {
                for (JsonNode x : arr) {
                    if (x.canConvertToInt() && x.asInt() >= 0) {
                        idx.add(x.asInt());
                    }
                }
            }
            if (!idx.isEmpty()) {
                out.put(uid, idx);
            }
        });
        return out;
    }

    private LeaderResolveInfo resolveLeaderFromPreset(StepExecutionContext context) {
        if (context.getMeeting() == null || context.getMeetingId() == null || context.getMeetingId().isBlank()) {
            return LeaderResolveInfo.notFound(null);
        }
        Integer presetCode = context.getMeeting().getPresetTypeCode();
        if (presetCode == null || presetCode <= 0) {
            return LeaderResolveInfo.notFound(null);
        }
        MeetingTypePreset preset = meetingTypePresetMapper.selectById(presetCode);
        if (preset == null) {
            return LeaderResolveInfo.notFound(null);
        }
        String leaderName = trim(preset.getLeaderName());
        if (leaderName == null) {
            return LeaderResolveInfo.notFound(null);
        }
        ParticipantService.NameResolveResult result =
                participantService.resolveUniqueUserIdByMeetingAndName(context.getMeetingId(), leaderName);
        if ("resolved".equals(result.status()) && result.userId() != null) {
            return LeaderResolveInfo.resolved(leaderName, result.userId());
        }
        if ("ambiguous".equals(result.status())) {
            return LeaderResolveInfo.ambiguous(leaderName);
        }
        return LeaderResolveInfo.notFound(leaderName);
    }

    private LeaderResolveInfo resolveLeaderFromPresetDirect(Integer presetCode) {
        if (presetCode == null || presetCode <= 0) {
            return LeaderResolveInfo.notFound(null);
        }
        MeetingTypePreset preset = meetingTypePresetMapper.selectById(presetCode);
        if (preset == null) {
            return LeaderResolveInfo.notFound(null);
        }
        String leaderName = trim(preset.getLeaderName());
        if (leaderName == null) {
            return LeaderResolveInfo.notFound(null);
        }
        List<String> names = splitNames(leaderName);
        if (names.isEmpty()) {
            return LeaderResolveInfo.notFound(leaderName);
        }
        String leaderUserId = names.get(0);
        return LeaderResolveInfo.resolved(leaderName, leaderUserId);
    }

    private Integer resolvePresetCode(StepExecutionContext context, JsonNode cfg, JsonNode shared) {
        if (context.getMeeting() != null && context.getMeeting().getPresetTypeCode() != null) {
            return context.getMeeting().getPresetTypeCode();
        }
        int fromCfg = support.number(cfg, "presetTypeCode", -1);
        if (fromCfg > 0) {
            return fromCfg;
        }
        int fromShared = support.number(shared, "presetTypeCode", -1);
        if (fromShared > 0) {
            return fromShared;
        }
        return null;
    }

    private String resolvePresetName(Integer presetCode) {
        if (presetCode == null || presetCode <= 0) {
            return "";
        }
        MeetingTypePreset preset = meetingTypePresetMapper.selectById(presetCode);
        if (preset == null) {
            return "";
        }
        String name = trim(preset.getDisplayName());
        return name == null ? "" : name;
    }

    private String mergePresetCodeIntoShared(String sharedContextJson, int presetTypeCode) {
        ObjectNode n = support.newObject();
        JsonNode base = support.parseConfig(sharedContextJson);
        if (base.isObject()) {
            n.setAll((ObjectNode) base);
        }
        n.put("presetTypeCode", presetTypeCode);
        return n.toString();
    }

    private boolean notifyLeaderResolveIssueIfNeeded(StepExecutionContext context, JsonNode cfg, LeaderResolveInfo leaderResolve) {
        if ("resolved".equals(leaderResolve.status)) {
            return false;
        }
        if (!support.bool(cfg, "notifyLeaderResolveIssue", true)) {
            return false;
        }
        if (context.getMeeting() == null) {
            return false;
        }
        Set<String> receivers = resolveOrganizerReceivers(context);
        if (receivers.isEmpty()) {
            return false;
        }
        String reason = "not_found".equals(leaderResolve.status) ? "未在本次参会人中匹配到该姓名"
                : ("ambiguous".equals(leaderResolve.status) ? "姓名匹配到多个参会人（重名）" : "未知原因");
        String msg = "会前会序确认提醒：leader_name 自动映射失败\n"
                + "会议：" + (context.getMeeting().getTitle() == null ? "" : context.getMeeting().getTitle()) + "\n"
                + "meetingId：" + context.getMeetingId() + "\n"
                + "leader_name：" + (leaderResolve.leaderName == null ? "(空)" : leaderResolve.leaderName) + "\n"
                + "原因：" + reason + "\n"
                + "请在模板中修正 leader_name，或在步骤配置里手动填写 leaderUserIds。";
        boolean sent = false;
        for (String receiver : receivers) {
            sent = feishuService.sendMessageToUserId(receiver, msg) || sent;
        }
        return sent;
    }

    private Set<String> resolveOrganizerReceivers(StepExecutionContext context) {
        Set<String> receivers = new LinkedHashSet<>();
        Integer presetCode = context.getMeeting().getPresetTypeCode();
        if (presetCode == null || presetCode <= 0) {
            return receivers;
        }
        MeetingTypePreset preset = meetingTypePresetMapper.selectById(presetCode);
        if (preset == null) {
            return receivers;
        }
        for (String organizerName : splitNames(preset.getOrganizerName())) {
            ParticipantService.NameResolveResult result =
                    participantService.resolveUniqueUserIdByMeetingAndName(context.getMeetingId(), organizerName);
            if ("resolved".equals(result.status()) && result.userId() != null) {
                receivers.add(result.userId());
            }
        }
        return receivers;
    }

    private List<String> splitNames(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String normalized = raw
                .replace("、", ",")
                .replace("，", ",")
                .replace("；", ",")
                .replace(";", ",")
                .replace("以及", ",");
        String[] arr = normalized.split(",");
        List<String> out = new ArrayList<>();
        for (String part : arr) {
            String v = trim(part);
            if (v != null) {
                out.add(v);
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

    private static class LeaderResolveInfo {
        final String leaderName;
        final String userId;
        final String status;

        private LeaderResolveInfo(String leaderName, String userId, String status) {
            this.leaderName = leaderName;
            this.userId = userId;
            this.status = status;
        }

        static LeaderResolveInfo resolved(String leaderName, String userId) {
            return new LeaderResolveInfo(leaderName, userId, "resolved");
        }

        static LeaderResolveInfo notFound(String leaderName) {
            return new LeaderResolveInfo(leaderName, null, "not_found");
        }

        static LeaderResolveInfo ambiguous(String leaderName) {
            return new LeaderResolveInfo(leaderName, null, "ambiguous");
        }
    }
}

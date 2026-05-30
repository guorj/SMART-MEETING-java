package com.smartmeeting.pipeline.executor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartmeeting.entity.MeetingTypePreset;
import com.smartmeeting.pipeline.StepExecutionContext;
import com.smartmeeting.pipeline.StepExecutionResult;
import com.smartmeeting.pipeline.StepExecutor;
import com.smartmeeting.service.AgendaFillCampaignService;
import com.smartmeeting.service.ParticipantService;
import com.smartmeeting.repository.MeetingTypePresetMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class PreAgendaFillInitStepExecutor implements StepExecutor {

    private final AgendaFillCampaignService agendaFillCampaignService;
    private final MeetingTypePresetMapper meetingTypePresetMapper;
    private final ParticipantService participantService;
    private final PipelineExecutorSupport support;

    @Override
    public String stepType() {
        return "pre-agenda-fill-init";
    }

    @Override
    public StepExecutionResult execute(StepExecutionContext context) {
        JsonNode cfg = support.parseConfig(context.getStep().getConfigJson());
        AgendaFillCampaignService.CampaignCreateRequest req = new AgendaFillCampaignService.CampaignCreateRequest();
        req.setMeetingId(context.getMeetingId());
        int presetCode = cfg.path("presetCode").asInt(0);
        if (presetCode <= 0 && context.getMeeting() != null && context.getMeeting().getPresetTypeCode() != null) {
            presetCode = context.getMeeting().getPresetTypeCode();
        }
        req.setPresetCode(presetCode > 0 ? presetCode : null);
        req.setExpireMinutes(cfg.path("expireMinutes").asInt(180));
        Map<String, List<Integer>> participantAgenda = parseParticipantAgenda(cfg.path("participantAgenda"));
        if (participantAgenda.isEmpty() && context.getMeetingId() != null && !context.getMeetingId().isBlank()) {
            participantAgenda = agendaFillCampaignService.resolveParticipantAgendaFromMeeting(context.getMeetingId());
        }
        if (participantAgenda.isEmpty() && req.getPresetCode() != null) {
            participantAgenda = agendaFillCampaignService.resolveParticipantAgendaFromPreset(req.getPresetCode());
        }
        req.setParticipantAgenda(participantAgenda);
        List<String> leaderUserIds = parseStringList(cfg.path("leaderUserIds"));
        LeaderResolveInfo leaderResolve = resolveLeaderFromPreset(context);
        if ("resolved".equals(leaderResolve.status) && leaderResolve.userId != null
                && leaderUserIds.stream().noneMatch(leaderResolve.userId::equals)) {
            leaderUserIds.add(leaderResolve.userId);
        }
        req.setLeaderUserIds(leaderUserIds);
        AgendaFillCampaignService.CampaignCreated created = agendaFillCampaignService.createCampaign(req);

        ObjectNode out = support.newObject();
        out.put("agendaFillCampaignId", created.getCampaignId());
        out.put("agendaFillMeetingId", context.getMeetingId());
        out.put("agendaFillPresetCode", created.getPresetCode());
        out.put("leaderNameFromPreset", leaderResolve.leaderName == null ? "" : leaderResolve.leaderName);
        out.put("leaderResolvedUserId", leaderResolve.userId == null ? "" : leaderResolve.userId);
        out.put("leaderResolveStatus", leaderResolve.status);
        ObjectNode tokenByUser = out.putObject("agendaFillTokenByUser");
        if (created.getTokenByUser() != null) {
            created.getTokenByUser().forEach(tokenByUser::put);
        }
        ArrayNode leaders = out.putArray("agendaFillLeaderUserIds");
        for (String uid : req.getLeaderUserIds()) {
            leaders.add(uid);
        }
        return StepExecutionResult.ok("agenda-fill-init-ok", out.toString());
    }

    private Map<String, List<Integer>> parseParticipantAgenda(JsonNode n) {
        Map<String, List<Integer>> out = new HashMap<>();
        if (n == null || !n.isObject()) {
            return out;
        }
        n.fieldNames().forEachRemaining(uid -> {
            JsonNode arr = n.path(uid);
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


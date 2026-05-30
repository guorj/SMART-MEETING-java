package com.smartmeeting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.config.agenda.HostAgendaDocBinding;
import com.smartmeeting.config.agenda.HostAgendaItem;
import com.smartmeeting.config.agenda.HostAgendaJsonCodec;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.entity.MeetingTypePreset;
import com.smartmeeting.exception.BusinessException;
import com.smartmeeting.repository.MeetingMapper;
import com.smartmeeting.repository.MeetingTypePresetMapper;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会前会序资料回填活动服务：
 * 1) 生成回填 token（按用户粒度）；
 * 2) 校验 token 权限；
 * 3) 将用户回填写入当前会议 host_agenda（默认）或预设 host_agenda（兼容旧链路）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgendaFillCampaignService {

    private final MeetingTypePresetMapper presetMapper;
    private final MeetingMapper meetingMapper;
    private final PresetAgendaDocService presetAgendaDocService;
    private final ObjectMapper objectMapper;

    private final Map<String, CampaignTokenScope> tokenScopes = new ConcurrentHashMap<>();
    private final Map<String, String> campaignUserToken = new ConcurrentHashMap<>();
    private final Map<String, Object> targetLocks = new ConcurrentHashMap<>();

    public CampaignCreated createCampaign(CampaignCreateRequest request) {
        if (request == null) {
            throw new BusinessException(400, "请求不能为空");
        }
        String meetingId = trim(request.getMeetingId());
        Integer presetCode = request.getPresetCode();
        if ((meetingId == null || meetingId.isBlank()) && presetCode == null) {
            throw new BusinessException(400, "meetingId 或 presetCode 需至少提供一个");
        }
        Meeting meeting = null;
        if (meetingId != null) {
            meeting = meetingMapper.selectById(meetingId);
            if (meeting == null) {
                throw new BusinessException(404, "meeting 不存在: " + meetingId);
            }
        } else {
            MeetingTypePreset preset = presetMapper.selectById(presetCode);
            if (preset == null) {
                throw new BusinessException(404, "preset 不存在: " + presetCode);
            }
        }
        String campaignId = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime expireAt = LocalDateTime.now().plusMinutes(Math.max(5, request.getExpireMinutes()));
        if ((request.getParticipantAgenda() == null || request.getParticipantAgenda().isEmpty())) {
            if (meetingId != null) {
                request.setParticipantAgenda(resolveParticipantAgendaFromMeeting(meetingId));
            } else if (presetCode != null) {
                request.setParticipantAgenda(resolveParticipantAgendaFromPreset(presetCode));
            }
        }

        Map<String, String> tokenByUser = new LinkedHashMap<>();
        if (request.getParticipantAgenda() != null) {
            for (Map.Entry<String, List<Integer>> e : request.getParticipantAgenda().entrySet()) {
                String userId = trim(e.getKey());
                if (userId == null) {
                    continue;
                }
                String token = newToken();
                tokenScopes.put(token, CampaignTokenScope.builder()
                        .campaignId(campaignId)
                        .meetingId(meetingId)
                        .presetCode(presetCode)
                        .operatorUserId(userId)
                        .leader(false)
                        .allowedAgendaIndexes(toSafeIndexSet(e.getValue()))
                        .expireAt(expireAt)
                        .build());
                tokenByUser.put(userId, token);
                campaignUserToken.put(campaignUserKey(campaignId, userId), token);
            }
        }

        if (request.getLeaderUserIds() != null) {
            for (String leader : request.getLeaderUserIds()) {
                String leaderId = trim(leader);
                if (leaderId == null) {
                    continue;
                }
                String token = tokenByUser.get(leaderId);
                if (token == null) {
                    token = newToken();
                    tokenByUser.put(leaderId, token);
                }
                tokenScopes.put(token, CampaignTokenScope.builder()
                        .campaignId(campaignId)
                        .meetingId(meetingId)
                        .presetCode(presetCode)
                        .operatorUserId(leaderId)
                        .leader(true)
                        .allowedAgendaIndexes(Set.of())
                        .expireAt(expireAt)
                        .build());
                campaignUserToken.put(campaignUserKey(campaignId, leaderId), token);
            }
        }

        return CampaignCreated.builder()
                .campaignId(campaignId)
                .meetingId(meetingId)
                .presetCode(presetCode)
                .expireAt(expireAt)
                .tokenByUser(tokenByUser)
                .build();
    }

    public SessionView loadSession(String token) {
        CampaignTokenScope scope = requireScope(token);
        List<HostAgendaItem> items = loadTargetAgendaItems(scope);
        List<AgendaItemView> viewItems = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            if (!scope.isLeader() && !scope.getAllowedAgendaIndexes().contains(i)) {
                continue;
            }
            HostAgendaItem item = items.get(i);
            viewItems.add(AgendaItemView.builder()
                    .agendaIndex(i)
                    .title(item.getTitle())
                    .minutes(item.getMinutes())
                    .url(resolvePrimaryUrl(item))
                    .build());
        }
        return SessionView.builder()
                .campaignId(scope.getCampaignId())
                .meetingId(scope.getMeetingId())
                .presetCode(scope.getPresetCode())
                .operatorUserId(scope.getOperatorUserId())
                .leader(scope.isLeader())
                .expireAt(scope.getExpireAt())
                .items(viewItems)
                .build();
    }

    public SubmitResult submit(String token, List<AgendaFillUpdate> updates) {
        CampaignTokenScope scope = requireScope(token);
        if (updates == null || updates.isEmpty()) {
            return SubmitResult.builder().updatedCount(0).build();
        }
        String targetKey = targetLockKey(scope);
        Object lock = targetLocks.computeIfAbsent(targetKey, k -> new Object());
        synchronized (lock) {
            List<HostAgendaItem> items = loadTargetAgendaItems(scope);
            int updated = 0;
            for (AgendaFillUpdate update : updates) {
                if (update == null || update.getAgendaIndex() == null) {
                    continue;
                }
                int idx = update.getAgendaIndex();
                if (idx < 0 || idx >= items.size()) {
                    throw new BusinessException(400, "agendaIndex 越界: " + idx);
                }
                if (!scope.isLeader() && !scope.getAllowedAgendaIndexes().contains(idx)) {
                    throw new BusinessException(403, "无权限修改 agendaIndex=" + idx);
                }
                HostAgendaItem item = items.get(idx);
                boolean changed = false;
                if (update.getMinutes() != null && update.getMinutes() > 0) {
                    item.setMinutes(update.getMinutes());
                    changed = true;
                }
                String url = trim(update.getUrl());
                if (url != null) {
                    upsertSourceUrls(item, splitUrls(url));
                    changed = true;
                }
                if (changed) {
                    updated++;
                }
            }
            saveTargetAgendaItems(scope, items);
            return SubmitResult.builder().updatedCount(updated).build();
        }
    }

    public SubmitResult submitByCampaignUser(String campaignId, String userId, List<AgendaFillUpdate> updates) {
        String token = claimTokenForUser(campaignId, userId);
        return submit(token, updates);
    }

    public String claimTokenForUser(String campaignId, String userId) {
        String c = trim(campaignId);
        String u = trim(userId);
        if (c == null || u == null) {
            throw new BusinessException(400, "campaignId/userId 不能为空");
        }
        String token = campaignUserToken.get(campaignUserKey(c, u));
        if (token == null) {
            throw new BusinessException(403, "当前用户不在本次回填授权范围");
        }
        requireScope(token);
        return token;
    }

    public Map<String, List<Integer>> resolveParticipantAgendaFromPreset(Integer presetCode) {
        if (presetCode == null) {
            return Map.of();
        }
        MeetingTypePreset preset = presetMapper.selectById(presetCode);
        if (preset == null) {
            return Map.of();
        }
        List<HostAgendaItem> items = HostAgendaJsonCodec.parseItems(objectMapper, preset.getHostAgenda());
        return extractOwnersAgenda(items);
    }

    public Map<String, List<Integer>> resolveParticipantAgendaFromMeeting(String meetingId) {
        String m = trim(meetingId);
        if (m == null) {
            return Map.of();
        }
        Meeting meeting = meetingMapper.selectById(m);
        if (meeting == null) {
            return Map.of();
        }
        List<HostAgendaItem> items = HostAgendaJsonCodec.parseItems(objectMapper, meeting.getHostAgenda());
        return extractOwnersAgenda(items);
    }

    private CampaignTokenScope requireScope(String token) {
        String t = trim(token);
        if (t == null) {
            throw new BusinessException(400, "token 不能为空");
        }
        CampaignTokenScope scope = tokenScopes.get(t);
        if (scope == null) {
            throw new BusinessException(404, "token 无效");
        }
        if (scope.getExpireAt() != null && scope.getExpireAt().isBefore(LocalDateTime.now())) {
            tokenScopes.remove(t);
            throw new BusinessException(410, "token 已过期");
        }
        return scope;
    }

    private List<HostAgendaItem> loadTargetAgendaItems(CampaignTokenScope scope) {
        if (scope.getMeetingId() != null && !scope.getMeetingId().isBlank()) {
            Meeting meeting = meetingMapper.selectById(scope.getMeetingId());
            if (meeting == null) {
                throw new BusinessException(404, "meeting 不存在: " + scope.getMeetingId());
            }
            return HostAgendaJsonCodec.parseItems(objectMapper, meeting.getHostAgenda());
        }
        if (scope.getPresetCode() != null) {
            MeetingTypePreset preset = presetMapper.selectById(scope.getPresetCode());
            if (preset == null) {
                throw new BusinessException(404, "preset 不存在: " + scope.getPresetCode());
            }
            return HostAgendaJsonCodec.parseItems(objectMapper, preset.getHostAgenda());
        }
        throw new BusinessException(400, "回填目标缺失");
    }

    private void saveTargetAgendaItems(CampaignTokenScope scope, List<HostAgendaItem> items) {
        String json = HostAgendaJsonCodec.toJson(objectMapper, items);
        if (scope.getMeetingId() != null && !scope.getMeetingId().isBlank()) {
            Meeting meeting = meetingMapper.selectById(scope.getMeetingId());
            if (meeting == null) {
                throw new BusinessException(404, "meeting 不存在: " + scope.getMeetingId());
            }
            meeting.setHostAgenda(json);
            meetingMapper.updateById(meeting);
            return;
        }
        if (scope.getPresetCode() != null) {
            MeetingTypePreset preset = presetMapper.selectById(scope.getPresetCode());
            if (preset == null) {
                throw new BusinessException(404, "preset 不存在: " + scope.getPresetCode());
            }
            preset.setHostAgenda(json);
            presetMapper.updateById(preset);
            presetAgendaDocService.refreshPresetBundle(scope.getPresetCode());
            return;
        }
        throw new BusinessException(400, "回填目标缺失");
    }

    private String targetLockKey(CampaignTokenScope scope) {
        if (scope.getMeetingId() != null && !scope.getMeetingId().isBlank()) {
            return "MEETING:" + scope.getMeetingId();
        }
        return "PRESET:" + scope.getPresetCode();
    }

    private Map<String, List<Integer>> extractOwnersAgenda(List<HostAgendaItem> items) {
        Map<String, List<Integer>> map = new LinkedHashMap<>();
        for (int i = 0; i < items.size(); i++) {
            HostAgendaItem item = items.get(i);
            if (item == null || item.getOwners() == null || item.getOwners().isEmpty()) {
                continue;
            }
            for (String owner : item.getOwners()) {
                String uid = trim(owner);
                if (uid == null) {
                    continue;
                }
                map.computeIfAbsent(uid, k -> new ArrayList<>()).add(i);
            }
        }
        return map;
    }

    private void upsertSourceUrls(HostAgendaItem item, List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return;
        }
        List<HostAgendaDocBinding> existing = item.getDocs() == null
                ? new ArrayList<>()
                : new ArrayList<>(item.getDocs());
        List<HostAgendaDocBinding> kept = new ArrayList<>();
        for (HostAgendaDocBinding doc : existing) {
            if (doc == null) {
                continue;
            }
            if (!isSourceRole(doc.getRole())) {
                kept.add(doc);
            }
        }
        for (int i = 0; i < urls.size(); i++) {
            kept.add(HostAgendaDocBinding.builder()
                    .role("SOURCE")
                    .slot(i)
                    .enabled(true)
                    .url(urls.get(i))
                    .build());
        }
        item.setDocs(kept);
        item.setFeishuDocUrl(urls.get(0));
    }

    private String resolvePrimaryUrl(HostAgendaItem item) {
        if (item.getDocs() != null && !item.getDocs().isEmpty()) {
            for (HostAgendaDocBinding doc : item.getDocs()) {
                if (doc == null || !isSourceRole(doc.getRole())) {
                    continue;
                }
                String url = trim(doc.getUrl());
                if (url != null) {
                    return url;
                }
            }
        }
        return trim(item.getFeishuDocUrl());
    }

    private List<String> splitUrls(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String normalized = raw
                .replace('，', ',')
                .replace('；', ',')
                .replace(';', ',')
                .replace('\n', ',');
        String[] arr = normalized.split(",");
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String part : arr) {
            String v = trim(part);
            if (v != null) {
                out.add(v);
            }
        }
        return new ArrayList<>(out);
    }

    private boolean isSourceRole(String role) {
        if (role == null || role.isBlank()) {
            return true;
        }
        String r = role.trim().toUpperCase(Locale.ROOT);
        return "SOURCE".equals(r) || "BOTH".equals(r);
    }

    private Set<Integer> toSafeIndexSet(List<Integer> indexes) {
        if (indexes == null || indexes.isEmpty()) {
            return Collections.emptySet();
        }
        return Set.copyOf(indexes.stream().filter(i -> i != null && i >= 0).toList());
    }

    private String newToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String campaignUserKey(String campaignId, String userId) {
        return campaignId + ":" + userId;
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        return v.isEmpty() ? null : v;
    }

    @Data
    public static class CampaignCreateRequest {
        private String meetingId;
        private Integer presetCode;
        private int expireMinutes = 180;
        private Map<String, List<Integer>> participantAgenda = new HashMap<>();
        private List<String> leaderUserIds = List.of();
    }

    @Data
    @Builder
    public static class CampaignCreated {
        private String campaignId;
        private String meetingId;
        private Integer presetCode;
        private LocalDateTime expireAt;
        private Map<String, String> tokenByUser;
    }

    @Data
    @Builder
    private static class CampaignTokenScope {
        private String campaignId;
        private String meetingId;
        private Integer presetCode;
        private String operatorUserId;
        private boolean leader;
        private Set<Integer> allowedAgendaIndexes;
        private LocalDateTime expireAt;
    }

    @Data
    @Builder
    public static class SessionView {
        private String campaignId;
        private String meetingId;
        private Integer presetCode;
        private String operatorUserId;
        private boolean leader;
        private LocalDateTime expireAt;
        private List<AgendaItemView> items;
    }

    @Data
    @Builder
    public static class AgendaItemView {
        private Integer agendaIndex;
        private String title;
        private Integer minutes;
        private String url;
    }

    @Data
    public static class AgendaFillUpdate {
        private Integer agendaIndex;
        private Integer minutes;
        private String url;
    }

    @Data
    @Builder
    public static class SubmitResult {
        private int updatedCount;
    }
}


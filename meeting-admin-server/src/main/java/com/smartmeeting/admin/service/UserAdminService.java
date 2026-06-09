package com.smartmeeting.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartmeeting.admin.api.dto.DashboardGrantEntryDto;
import com.smartmeeting.admin.api.dto.DashboardGrantPolicyDto;
import com.smartmeeting.admin.api.dto.UserDashboardGrantDto;
import com.smartmeeting.admin.api.dto.UserMappingDto;
import com.smartmeeting.admin.api.dto.UserProfileDetailDto;
import com.smartmeeting.admin.api.dto.UserProfileSaveDto;
import com.smartmeeting.admin.api.dto.UserProfileSummaryDto;
import com.smartmeeting.admin.api.dto.VoiceprintDto;
import com.smartmeeting.admin.entity.UserMapping;
import com.smartmeeting.admin.entity.Voiceprint;
import com.smartmeeting.admin.exception.BusinessException;
import com.smartmeeting.admin.config.MeetingVoiceprintLifecycleProperties;
import com.smartmeeting.admin.repository.UserMappingMapper;
import com.smartmeeting.admin.repository.VoiceprintMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserAdminService {

    private final UserMappingMapper userMappingMapper;
    private final VoiceprintMapper voiceprintMapper;
    private final DashboardGrantAdminService dashboardGrantAdminService;
    private final MeetingVoiceprintLifecycleProperties lifecycleProperties;

    public Page<UserMappingDto> listMappings(int page, int size, String keyword) {
        LambdaQueryWrapper<UserMapping> q = new LambdaQueryWrapper<>();
        applyMappingKeyword(q, keyword);
        q.orderByAsc(UserMapping::getUserId);
        Page<UserMapping> raw = userMappingMapper.selectPage(new Page<>(page, size), q);
        Page<UserMappingDto> out = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        out.setRecords(raw.getRecords().stream().map(this::toMappingDto).toList());
        return out;
    }

    public UserMappingDto getMapping(int userId) {
        UserMapping row = requireMapping(userId);
        return toMappingDto(row);
    }

    public int createMapping(UserMappingDto body) {
        if (body.getUserId() == null || body.getUserId() <= 0) {
            throw new BusinessException("userId must be a positive integer");
        }
        if (!StringUtils.hasText(body.getUserName())) {
            throw new BusinessException("userName required");
        }
        if (userMappingMapper.selectById(body.getUserId()) != null) {
            throw new BusinessException("user mapping already exists: " + body.getUserId());
        }
        UserMapping row = fromMappingDto(body);
        userMappingMapper.insert(row);
        return row.getUserId();
    }

    public void updateMapping(int userId, UserMappingDto body) {
        requireMapping(userId);
        if (!StringUtils.hasText(body.getUserName())) {
            throw new BusinessException("userName required");
        }
        UserMapping row = fromMappingDto(body);
        row.setUserId(userId);
        userMappingMapper.updateById(row);
    }

    public void deleteMapping(int userId) {
        requireMapping(userId);
        userMappingMapper.deleteById(userId);
    }

    public Page<VoiceprintDto> listVoiceprints(int page, int size, String keyword, String expiryFilter) {
        LambdaQueryWrapper<Voiceprint> q = new LambdaQueryWrapper<>();
        applyVoiceprintKeyword(q, keyword);
        applyExpiryFilter(q, expiryFilter);
        q.orderByDesc(Voiceprint::getRegisteredAt);
        Page<Voiceprint> raw = voiceprintMapper.selectPage(new Page<>(page, size), q);
        Page<VoiceprintDto> out = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        out.setRecords(raw.getRecords().stream().map(this::toVoiceprintDto).toList());
        return out;
    }

    public VoiceprintDto getVoiceprint(String id) {
        Voiceprint row = requireVoiceprint(id);
        return toVoiceprintDto(row);
    }

    public String createVoiceprint(VoiceprintDto body) {
        validateVoiceprintBody(body, true);
        Voiceprint row = fromVoiceprintDto(body);
        if (!StringUtils.hasText(row.getId())) {
            row.setId(UUID.randomUUID().toString());
        }
        if (voiceprintMapper.selectById(row.getId()) != null) {
            throw new BusinessException("voiceprint id already exists: " + row.getId());
        }
        fillVoiceprintDefaults(row);
        validateVoiceprintDates(row);
        voiceprintMapper.insert(row);
        return row.getId();
    }

    public void updateVoiceprint(String id, VoiceprintDto body) {
        Voiceprint existing = requireVoiceprint(id);
        validateVoiceprintBody(body, false);
        Voiceprint row = fromVoiceprintDto(body);
        row.setId(id);
        if (row.getRegisteredAt() == null) {
            row.setRegisteredAt(existing.getRegisteredAt() != null
                    ? existing.getRegisteredAt() : LocalDateTime.now());
        }
        if (row.getExpiresAt() == null) {
            row.setExpiresAt(existing.getExpiresAt() != null
                    ? existing.getExpiresAt()
                    : row.getRegisteredAt().plusYears(lifecycleProperties.getExpireYears()));
        }
        if (!row.getExpiresAt().isAfter(row.getRegisteredAt())) {
            throw new BusinessException("expiresAt must be after registeredAt");
        }
        voiceprintMapper.updateById(row);
    }

    public void deleteVoiceprint(String id) {
        requireVoiceprint(id);
        voiceprintMapper.deleteById(id);
    }

    // —— 用户档案（映射 + 声纹聚合，便于扩展更多用户表） ——

    public Page<UserProfileSummaryDto> listProfiles(int page, int size, String keyword, String expiryFilter) {
        LambdaQueryWrapper<UserMapping> q = new LambdaQueryWrapper<>();
        applyProfileListFilters(q, keyword, expiryFilter);
        q.orderByAsc(UserMapping::getUserId);
        Page<UserMapping> raw = userMappingMapper.selectPage(new Page<>(page, size), q);
        List<Integer> userIds = raw.getRecords().stream().map(UserMapping::getUserId).toList();
        Map<Integer, List<Voiceprint>> vpByUser = loadVoiceprintsByUserIds(userIds);
        Map<String, DashboardGrantEntryDto> grantByFeishuId = loadGrantEntriesByFeishuId();

        Page<UserProfileSummaryDto> out = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        out.setRecords(raw.getRecords().stream()
                .map(m -> toProfileSummary(m, vpByUser.getOrDefault(m.getUserId(), List.of()), grantByFeishuId))
                .toList());
        return out;
    }

    public DashboardGrantPolicyDto getGrantPolicy() {
        return dashboardGrantAdminService.getGrantPolicy();
    }

    public DashboardGrantPolicyDto updateGrantPolicy(DashboardGrantPolicyDto policy) {
        return dashboardGrantAdminService.updateGrantPolicy(policy);
    }

    /**
     * 一键应用前台授权预设。
     *
     * @param userId  OA userId
     * @param preset  BASIC=访问前台+注册声纹；FULL=含建会/结束会
     */
    public UserDashboardGrantDto applyQuickDashboardGrant(int userId, String preset) {
        UserMapping mapping = requireMapping(userId);
        if (!StringUtils.hasText(mapping.getFeishuUserId())) {
            throw new BusinessException("一键授权前须先填写 feishu_user_id");
        }
        UserDashboardGrantDto grant = buildQuickGrantPreset(preset);
        syncDashboardGrant(toMappingDto(mapping), grant, mapping.getFeishuUserId());
        return grant;
    }

    private UserDashboardGrantDto buildQuickGrantPreset(String preset) {
        String mode = preset == null ? "BASIC" : preset.trim().toUpperCase();
        UserDashboardGrantDto grant = new UserDashboardGrantDto();
        grant.setEnabled(true);
        grant.setCanRegisterVoiceprint(true);
        if ("FULL".equals(mode)) {
            grant.setCanCreateMeeting(true);
            grant.setCanEndMeeting(true);
        } else if ("BASIC".equals(mode)) {
            grant.setCanCreateMeeting(false);
            grant.setCanEndMeeting(false);
        } else {
            throw new BusinessException("未知授权预设: " + preset + "（可用 BASIC / FULL）");
        }
        return grant;
    }

    public UserProfileDetailDto getProfile(int userId) {
        UserMapping mapping = userMappingMapper.selectById(userId);
        if (mapping == null) {
            throw new BusinessException("user not found: " + userId);
        }
        List<VoiceprintDto> voiceprints = listVoiceprintsByUserId(userId).stream()
                .map(this::toVoiceprintDto)
                .toList();
        UserProfileDetailDto dto = new UserProfileDetailDto();
        dto.setMapping(toMappingDto(mapping));
        dto.setVoiceprints(voiceprints);
        dto.setPrimaryVoiceprint(voiceprints.isEmpty() ? null : voiceprints.get(0));
        dto.setDashboardGrant(toUserDashboardGrant(dashboardGrantAdminService.findEntry(mapping.getFeishuUserId())));
        return dto;
    }

    public int createProfile(UserProfileSaveDto body) {
        if (body == null || body.getMapping() == null) {
            throw new BusinessException("mapping required");
        }
        UserMappingDto mapping = body.getMapping();
        int userId = createMapping(mapping);
        if (body.isClearVoiceprint()) {
            deleteVoiceprintsByUserId(userId);
        } else {
            upsertVoiceprintForUser(userId, mapping.getUserName(), mapping.getFeishuUserId(), body.getVoiceprint());
        }
        syncDashboardGrant(mapping, body.getDashboardGrant(), null);
        return userId;
    }

    public void updateProfile(int userId, UserProfileSaveDto body) {
        if (body == null || body.getMapping() == null) {
            throw new BusinessException("mapping required");
        }
        UserMapping existing = requireMapping(userId);
        String previousFeishuUserId = existing.getFeishuUserId();
        UserMappingDto mapping = body.getMapping();
        mapping.setUserId(userId);
        updateMapping(userId, mapping);
        if (body.isClearVoiceprint()) {
            deleteVoiceprintsByUserId(userId);
        } else {
            upsertVoiceprintForUser(userId, mapping.getUserName(), mapping.getFeishuUserId(), body.getVoiceprint());
        }
        syncDashboardGrant(mapping, body.getDashboardGrant(), previousFeishuUserId);
    }

    public void deleteProfile(int userId) {
        UserMapping mapping = userMappingMapper.selectById(userId);
        if (mapping != null && StringUtils.hasText(mapping.getFeishuUserId())) {
            removeGrantEntryIfExists(mapping.getFeishuUserId());
        }
        deleteVoiceprintsByUserId(userId);
        if (mapping != null) {
            userMappingMapper.deleteById(userId);
        }
    }

    private void syncDashboardGrant(UserMappingDto mapping,
                                    UserDashboardGrantDto grant,
                                    String previousFeishuUserId) {
        if (grant == null) {
            return;
        }
        String feishuUserId = trimToNull(mapping.getFeishuUserId());
        if (StringUtils.hasText(previousFeishuUserId)
                && feishuUserId != null
                && !previousFeishuUserId.equals(feishuUserId)) {
            removeGrantEntryIfExists(previousFeishuUserId);
        }
        if (!StringUtils.hasText(feishuUserId)) {
            if (grant.isEnabled()) {
                throw new BusinessException("启用前台授权前须填写 feishu_user_id");
            }
            return;
        }
        if (!grant.isEnabled()) {
            removeGrantEntryIfExists(feishuUserId);
            return;
        }
        DashboardGrantEntryDto entry = new DashboardGrantEntryDto();
        entry.setFeishuUserId(feishuUserId);
        entry.setUserName(mapping.getUserName());
        entry.setEnabled(true);
        entry.setCanCreateMeeting(grant.isCanCreateMeeting());
        entry.setCanEndMeeting(grant.isCanEndMeeting());
        entry.setCanRegisterVoiceprint(grant.isCanRegisterVoiceprint());
        entry.setRemark(trimToNull(grant.getRemark()));
        if (dashboardGrantAdminService.findEntry(feishuUserId) != null) {
            dashboardGrantAdminService.updateEntry(entry);
        } else {
            dashboardGrantAdminService.addEntry(entry);
        }
    }

    private void removeGrantEntryIfExists(String feishuUserId) {
        if (!StringUtils.hasText(feishuUserId)) {
            return;
        }
        if (dashboardGrantAdminService.findEntry(feishuUserId) != null) {
            dashboardGrantAdminService.deleteEntry(feishuUserId);
        }
    }

    private Map<String, DashboardGrantEntryDto> loadGrantEntriesByFeishuId() {
        var config = dashboardGrantAdminService.getConfig();
        Map<String, DashboardGrantEntryDto> map = new HashMap<>();
        if (config.getEntries() == null) {
            return map;
        }
        for (DashboardGrantEntryDto entry : config.getEntries()) {
            if (StringUtils.hasText(entry.getFeishuUserId())) {
                map.put(entry.getFeishuUserId(), entry);
            }
        }
        return map;
    }

    private UserDashboardGrantDto toUserDashboardGrant(DashboardGrantEntryDto entry) {
        if (entry == null) {
            return null;
        }
        UserDashboardGrantDto dto = new UserDashboardGrantDto();
        dto.setEnabled(entry.isEnabled());
        dto.setCanCreateMeeting(entry.isCanCreateMeeting());
        dto.setCanEndMeeting(entry.isCanEndMeeting());
        dto.setCanRegisterVoiceprint(entry.isCanRegisterVoiceprint());
        dto.setRemark(entry.getRemark());
        return dto;
    }

    private void applyGrantSummary(UserProfileSummaryDto dto,
                                   String feishuUserId,
                                   Map<String, DashboardGrantEntryDto> grantByFeishuId) {
        if (!StringUtils.hasText(feishuUserId)) {
            dto.setDashboardGrantEnabled(false);
            dto.setDashboardCanCreateMeeting(false);
            return;
        }
        DashboardGrantEntryDto entry = grantByFeishuId.get(feishuUserId);
        if (entry == null || !entry.isEnabled()) {
            dto.setDashboardGrantEnabled(false);
            dto.setDashboardCanCreateMeeting(false);
            return;
        }
        dto.setDashboardGrantEnabled(true);
        dto.setDashboardCanCreateMeeting(entry.isCanCreateMeeting());
    }

    private void applyProfileListFilters(LambdaQueryWrapper<UserMapping> q, String keyword, String expiryFilter) {
        if (StringUtils.hasText(keyword)) {
            List<Integer> vpUserIds = findUserIdsByVoiceprintKeyword(keyword.trim());
            String kw = keyword.trim();
            Integer uid = tryParseUserId(kw);
            q.and(w -> {
                w.like(UserMapping::getUserName, kw)
                        .or().like(UserMapping::getFeishuUserId, kw);
                if (uid != null) {
                    w.or().eq(UserMapping::getUserId, uid);
                }
                if (!vpUserIds.isEmpty()) {
                    w.or().in(UserMapping::getUserId, vpUserIds);
                }
            });
        }
        if (StringUtils.hasText(expiryFilter) && !"ALL".equalsIgnoreCase(expiryFilter)) {
            List<Integer> filtered = findUserIdsByExpiryFilter(expiryFilter);
            if (filtered.isEmpty()) {
                q.eq(UserMapping::getUserId, -1);
            } else {
                q.in(UserMapping::getUserId, filtered);
            }
        }
    }

    private List<Integer> findUserIdsByVoiceprintKeyword(String kw) {
        LambdaQueryWrapper<Voiceprint> q = new LambdaQueryWrapper<>();
        Integer uid = tryParseUserId(kw);
        q.and(w -> {
            w.like(Voiceprint::getUserName, kw)
                    .or().like(Voiceprint::getFeishuUserId, kw)
                    .or().like(Voiceprint::getFeatureId, kw)
                    .or().like(Voiceprint::getGroupId, kw);
            if (uid != null) {
                w.or().eq(Voiceprint::getUserId, uid);
            }
        });
        return voiceprintMapper.selectList(q).stream()
                .map(Voiceprint::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private List<Integer> findUserIdsByExpiryFilter(String expiryFilter) {
        LambdaQueryWrapper<Voiceprint> q = new LambdaQueryWrapper<>();
        applyExpiryFilter(q, expiryFilter);
        return voiceprintMapper.selectList(q).stream()
                .map(Voiceprint::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private Map<Integer, List<Voiceprint>> loadVoiceprintsByUserIds(List<Integer> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        LambdaQueryWrapper<Voiceprint> q = new LambdaQueryWrapper<>();
        q.in(Voiceprint::getUserId, userIds).orderByDesc(Voiceprint::getRegisteredAt);
        return voiceprintMapper.selectList(q).stream()
                .collect(Collectors.groupingBy(Voiceprint::getUserId));
    }

    private List<Voiceprint> listVoiceprintsByUserId(int userId) {
        LambdaQueryWrapper<Voiceprint> q = new LambdaQueryWrapper<>();
        q.eq(Voiceprint::getUserId, userId).orderByDesc(Voiceprint::getRegisteredAt);
        return voiceprintMapper.selectList(q);
    }

    private void deleteVoiceprintsByUserId(int userId) {
        LambdaQueryWrapper<Voiceprint> q = new LambdaQueryWrapper<>();
        q.eq(Voiceprint::getUserId, userId);
        voiceprintMapper.delete(q);
    }

    private void upsertVoiceprintForUser(int userId, String userName, String feishuUserId, VoiceprintDto vp) {
        if (vp == null || !StringUtils.hasText(vp.getFeatureId())) {
            return;
        }
        vp.setUserId(userId);
        vp.setUserName(userName);
        if (!StringUtils.hasText(vp.getFeishuUserId())) {
            vp.setFeishuUserId(feishuUserId);
        }
        if (StringUtils.hasText(vp.getId())) {
            updateVoiceprint(vp.getId(), vp);
            return;
        }
        List<Voiceprint> existing = listVoiceprintsByUserId(userId);
        if (!existing.isEmpty()) {
            vp.setId(existing.get(0).getId());
            updateVoiceprint(vp.getId(), vp);
        } else {
            createVoiceprint(vp);
        }
    }

    private UserProfileSummaryDto toProfileSummary(UserMapping mapping,
                                                   List<Voiceprint> voiceprints,
                                                   Map<String, DashboardGrantEntryDto> grantByFeishuId) {
        UserProfileSummaryDto dto = new UserProfileSummaryDto();
        dto.setUserId(mapping.getUserId());
        dto.setUserName(mapping.getUserName());
        dto.setFeishuUserId(mapping.getFeishuUserId());
        dto.setVoiceprintCount(voiceprints.size());
        applyGrantSummary(dto, mapping.getFeishuUserId(), grantByFeishuId);
        if (voiceprints.isEmpty()) {
            dto.setHasVoiceprint(false);
            return dto;
        }
        Voiceprint primary = voiceprints.stream()
                .max(Comparator.comparing(Voiceprint::getRegisteredAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(voiceprints.get(0));
        dto.setHasVoiceprint(true);
        dto.setVoiceprintId(primary.getId());
        dto.setFeatureId(primary.getFeatureId());
        dto.setGroupId(primary.getGroupId());
        dto.setRegisteredAt(primary.getRegisteredAt());
        dto.setExpiresAt(primary.getExpiresAt());
        dto.setExpiryStatus(resolveExpiryStatus(primary.getExpiresAt()));
        return dto;
    }

    private UserMapping requireMapping(int userId) {
        UserMapping row = userMappingMapper.selectById(userId);
        if (row == null) {
            throw new BusinessException("user mapping not found: " + userId);
        }
        return row;
    }

    private Voiceprint requireVoiceprint(String id) {
        Voiceprint row = voiceprintMapper.selectById(id);
        if (row == null) {
            throw new BusinessException("voiceprint not found: " + id);
        }
        return row;
    }

    private void applyMappingKeyword(LambdaQueryWrapper<UserMapping> q, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return;
        }
        String kw = keyword.trim();
        Integer uid = tryParseUserId(kw);
        q.and(w -> {
            w.like(UserMapping::getUserName, kw)
                    .or().like(UserMapping::getFeishuUserId, kw);
            if (uid != null) {
                w.or().eq(UserMapping::getUserId, uid);
            }
        });
    }

    private void applyVoiceprintKeyword(LambdaQueryWrapper<Voiceprint> q, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return;
        }
        String kw = keyword.trim();
        Integer uid = tryParseUserId(kw);
        q.and(w -> {
            w.like(Voiceprint::getUserName, kw)
                    .or().like(Voiceprint::getFeishuUserId, kw)
                    .or().like(Voiceprint::getFeatureId, kw)
                    .or().like(Voiceprint::getGroupId, kw);
            if (uid != null) {
                w.or().eq(Voiceprint::getUserId, uid);
            }
        });
    }

    private static Integer tryParseUserId(String kw) {
        try {
            return Integer.parseInt(kw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void applyExpiryFilter(LambdaQueryWrapper<Voiceprint> q, String expiryFilter) {
        if (!StringUtils.hasText(expiryFilter) || "ALL".equalsIgnoreCase(expiryFilter)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        switch (expiryFilter.toUpperCase()) {
            case "VALID" -> q.gt(Voiceprint::getExpiresAt, now);
            case "EXPIRED" -> q.le(Voiceprint::getExpiresAt, now);
            case "EXPIRING" -> q.gt(Voiceprint::getExpiresAt, now)
                    .le(Voiceprint::getExpiresAt, now.plusHours(lifecycleProperties.getExpiringWarningHours()));
            default -> throw new BusinessException("invalid expiryFilter: " + expiryFilter);
        }
    }

    private void validateVoiceprintBody(VoiceprintDto body, boolean creating) {
        if (body.getUserId() == null) {
            throw new BusinessException("userId required");
        }
        if (body.getUserId() <= 0) {
            throw new BusinessException("userId must be a positive integer");
        }
        if (!StringUtils.hasText(body.getUserName())) {
            throw new BusinessException("userName required");
        }
        if (!StringUtils.hasText(body.getFeatureId())) {
            throw new BusinessException("featureId required");
        }
        if (creating && StringUtils.hasText(body.getId()) && voiceprintMapper.selectById(body.getId()) != null) {
            throw new BusinessException("voiceprint id already exists: " + body.getId());
        }
    }

    private void validateVoiceprintDates(Voiceprint row) {
        if (row.getRegisteredAt() != null && row.getExpiresAt() != null
                && !row.getExpiresAt().isAfter(row.getRegisteredAt())) {
            throw new BusinessException("expiresAt must be after registeredAt");
        }
    }

    private void fillVoiceprintDefaults(Voiceprint row) {
        if (row.getRegisteredAt() == null) {
            row.setRegisteredAt(LocalDateTime.now());
        }
        if (row.getExpiresAt() == null) {
            row.setExpiresAt(row.getRegisteredAt().plusYears(lifecycleProperties.getExpireYears()));
        }
    }

    private UserMappingDto toMappingDto(UserMapping row) {
        UserMappingDto dto = new UserMappingDto();
        dto.setUserId(row.getUserId());
        dto.setUserName(row.getUserName());
        dto.setFeishuUserId(row.getFeishuUserId());
        return dto;
    }

    private UserMapping fromMappingDto(UserMappingDto dto) {
        UserMapping row = new UserMapping();
        row.setUserId(dto.getUserId());
        row.setUserName(dto.getUserName().trim());
        row.setFeishuUserId(trimToNull(dto.getFeishuUserId()));
        return row;
    }

    private VoiceprintDto toVoiceprintDto(Voiceprint row) {
        VoiceprintDto dto = new VoiceprintDto();
        dto.setId(row.getId());
        dto.setUserId(row.getUserId());
        dto.setUserName(row.getUserName());
        dto.setFeishuUserId(row.getFeishuUserId());
        dto.setFeatureId(row.getFeatureId());
        dto.setGroupId(row.getGroupId());
        dto.setRegisteredAt(row.getRegisteredAt());
        dto.setExpiresAt(row.getExpiresAt());
        dto.setExpiryStatus(resolveExpiryStatus(row.getExpiresAt()));
        return dto;
    }

    private Voiceprint fromVoiceprintDto(VoiceprintDto dto) {
        Voiceprint row = new Voiceprint();
        row.setId(trimToNull(dto.getId()));
        row.setUserId(dto.getUserId());
        row.setUserName(dto.getUserName().trim());
        row.setFeishuUserId(trimToNull(dto.getFeishuUserId()));
        row.setFeatureId(dto.getFeatureId().trim());
        row.setGroupId(trimToNull(dto.getGroupId()));
        row.setRegisteredAt(dto.getRegisteredAt());
        row.setExpiresAt(dto.getExpiresAt());
        return row;
    }

    private String resolveExpiryStatus(LocalDateTime expiresAt) {
        if (expiresAt == null) {
            return "UNKNOWN";
        }
        LocalDateTime now = LocalDateTime.now();
        if (!expiresAt.isAfter(now)) {
            return "EXPIRED";
        }
        if (!expiresAt.isAfter(now.plusHours(lifecycleProperties.getExpiringWarningHours()))) {
            return "EXPIRING";
        }
        return "VALID";
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}

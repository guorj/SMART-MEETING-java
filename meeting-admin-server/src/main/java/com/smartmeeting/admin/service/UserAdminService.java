package com.smartmeeting.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartmeeting.admin.api.dto.UserMappingDto;
import com.smartmeeting.admin.api.dto.UserProfileDetailDto;
import com.smartmeeting.admin.api.dto.UserProfileSaveDto;
import com.smartmeeting.admin.api.dto.UserProfileSummaryDto;
import com.smartmeeting.admin.api.dto.VoiceprintDto;
import com.smartmeeting.admin.entity.UserMapping;
import com.smartmeeting.admin.entity.Voiceprint;
import com.smartmeeting.admin.exception.BusinessException;
import com.smartmeeting.admin.repository.UserMappingMapper;
import com.smartmeeting.admin.repository.VoiceprintMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserAdminService {

    private static final int VOICEPRINT_EXPIRE_YEARS = 10;
    private static final int EXPIRING_HOURS = 48;

    private final UserMappingMapper userMappingMapper;
    private final VoiceprintMapper voiceprintMapper;

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
                    : row.getRegisteredAt().plusYears(VOICEPRINT_EXPIRE_YEARS));
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

        Page<UserProfileSummaryDto> out = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        out.setRecords(raw.getRecords().stream()
                .map(m -> toProfileSummary(m, vpByUser.getOrDefault(m.getUserId(), List.of())))
                .toList());
        return out;
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
        return userId;
    }

    public void updateProfile(int userId, UserProfileSaveDto body) {
        if (body == null || body.getMapping() == null) {
            throw new BusinessException("mapping required");
        }
        UserMappingDto mapping = body.getMapping();
        mapping.setUserId(userId);
        updateMapping(userId, mapping);
        if (body.isClearVoiceprint()) {
            deleteVoiceprintsByUserId(userId);
            return;
        }
        upsertVoiceprintForUser(userId, mapping.getUserName(), mapping.getFeishuUserId(), body.getVoiceprint());
    }

    public void deleteProfile(int userId) {
        deleteVoiceprintsByUserId(userId);
        if (userMappingMapper.selectById(userId) != null) {
            userMappingMapper.deleteById(userId);
        }
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

    private UserProfileSummaryDto toProfileSummary(UserMapping mapping, List<Voiceprint> voiceprints) {
        UserProfileSummaryDto dto = new UserProfileSummaryDto();
        dto.setUserId(mapping.getUserId());
        dto.setUserName(mapping.getUserName());
        dto.setFeishuUserId(mapping.getFeishuUserId());
        dto.setVoiceprintCount(voiceprints.size());
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
                    .le(Voiceprint::getExpiresAt, now.plusHours(EXPIRING_HOURS));
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
            row.setExpiresAt(row.getRegisteredAt().plusYears(VOICEPRINT_EXPIRE_YEARS));
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
        if (!expiresAt.isAfter(now.plusHours(EXPIRING_HOURS))) {
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

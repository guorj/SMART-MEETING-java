package com.smartmeeting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmeeting.entity.UserMapping;
import com.smartmeeting.repository.UserMappingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 将参会人/创建人标识解析为飞书 {@code user_id}，供日历邀请、IM 等出站使用。
 * <p>
 * 解析顺序：占位符过滤 → 已是飞书 ID → OA 整数 {@code int_user_mapping_feishu.user_id} →
 * 按 {@code feishu_user_id} 列匹配 →  legacy 非空字符串兜底。
 */
@Service
@RequiredArgsConstructor
public class FeishuUserIdResolver {

    private final UserMappingMapper userMappingMapper;

    public Optional<String> resolveByUserName(String userName) {
        if (userName == null || userName.isBlank()) {
            return Optional.empty();
        }
        String trimmed = userName.trim();
        UserMapping mapping = userMappingMapper.selectOne(new LambdaQueryWrapper<UserMapping>()
                .eq(UserMapping::getUserName, trimmed)
                .last("LIMIT 1"));
        if (mapping != null && mapping.getFeishuUserId() != null && !mapping.getFeishuUserId().isBlank()) {
            return Optional.of(mapping.getFeishuUserId().trim());
        }
        return Optional.empty();
    }

    /**
     * 参会人行：优先 user_id；占位符 {@code vp_*} / {@code unknown_*} 时按姓名查映射表。
     */
    public Optional<String> resolveParticipant(String rawUserId, String participantName) {
        if (rawUserId != null && !rawUserId.isBlank() && !isPlaceholder(rawUserId.trim())) {
            return resolve(rawUserId);
        }
        return resolveByUserName(participantName);
    }

    public Optional<String> resolve(String rawUserId) {
        if (rawUserId == null || rawUserId.isBlank()) {
            return Optional.empty();
        }
        String trimmed = rawUserId.trim();
        if (isPlaceholder(trimmed)) {
            return Optional.empty();
        }
        if (looksLikeFeishuId(trimmed)) {
            return Optional.of(trimmed);
        }
        Optional<String> fromOa = resolveFromOaUserId(trimmed);
        if (fromOa.isPresent()) {
            return fromOa;
        }
        UserMapping byFeishu = userMappingMapper.selectOne(new LambdaQueryWrapper<UserMapping>()
                .eq(UserMapping::getFeishuUserId, trimmed)
                .last("LIMIT 1"));
        if (byFeishu != null && byFeishu.getFeishuUserId() != null && !byFeishu.getFeishuUserId().isBlank()) {
            return Optional.of(byFeishu.getFeishuUserId().trim());
        }
        if (trimmed.length() >= 6 && !trimmed.contains(" ")) {
            return Optional.of(trimmed);
        }
        return Optional.empty();
    }

    public List<String> resolveMany(Iterable<String> rawUserIds) {
        Set<String> out = new LinkedHashSet<>();
        if (rawUserIds == null) {
            return List.of();
        }
        for (String raw : rawUserIds) {
            resolve(raw).ifPresent(out::add);
        }
        return new ArrayList<>(out);
    }

    private Optional<String> resolveFromOaUserId(String trimmed) {
        try {
            int oaId = Integer.parseInt(trimmed);
            if (oaId <= 0) {
                return Optional.empty();
            }
            UserMapping mapping = userMappingMapper.selectById(oaId);
            if (mapping != null && mapping.getFeishuUserId() != null && !mapping.getFeishuUserId().isBlank()) {
                return Optional.of(mapping.getFeishuUserId().trim());
            }
        } catch (NumberFormatException ignored) {
            // not OA numeric id
        }
        return Optional.empty();
    }

    static boolean isPlaceholder(String id) {
        return id.startsWith("unknown_") || id.startsWith("vp_");
    }

    static boolean looksLikeFeishuId(String id) {
        return id.startsWith("ou_") || id.startsWith("on_");
    }
}

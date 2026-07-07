package com.smartmeeting.service.oabp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmeeting.entity.UserMapping;
import com.smartmeeting.repository.UserMappingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 责任人飞书 user_id → OABP {@code system_users.id}（数字工号）反查解析器。
 * <p>
 * 用于 meeting 待办同步到 oabp {@code jq_todos_subtask.asignee_id} 时，
 * 把 meeting 端的飞书 user_id 反查为 OABP 系统用户 ID（int）。
 * </p>
 * <p>
 * <b>映射依据</b>：{@code int_user_mapping_feishu.user_id}（Integer 主键）即 OABP
 * {@code system_users.id}，{@code feishu_user_id} 列存对应飞书 user_id。
 * 按飞书 user_id 反查即可得到 OABP 工号。
 * </p>
 * <p>
 * <b>未匹配处理</b>：查不到映射时返回 {@link Optional#empty()}，调用方应跳过 subtask 同步
 * （只同步 task + followup），避免阻塞 outbox 链路。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OabpAssigneeResolver {

    private final UserMappingMapper userMappingMapper;

    /**
     * 按飞书 user_id 反查 OABP 系统用户 ID。
     *
     * @param feishuUserId 责任人飞书 user_id；null/blank/占位符返回 empty
     * @return OABP 系统用户 ID（即 {@code int_user_mapping_feishu.user_id}）；未匹配返回 empty
     */
    public Optional<Integer> resolveOaUserId(String feishuUserId) {
        if (feishuUserId == null || feishuUserId.isBlank()) {
            return Optional.empty();
        }
        String trimmed = feishuUserId.trim();
        if (isPlaceholder(trimmed)) {
            return Optional.empty();
        }
        UserMapping mapping = userMappingMapper.selectOne(new LambdaQueryWrapper<UserMapping>()
                .eq(UserMapping::getFeishuUserId, trimmed)
                .last("LIMIT 1"));
        if (mapping == null || mapping.getUserId() == null) {
            log.debug("OabpAssigneeResolver no mapping for feishuUserId={}", trimmed);
            return Optional.empty();
        }
        return Optional.of(mapping.getUserId());
    }

    private static boolean isPlaceholder(String id) {
        return id.startsWith("unknown_") || id.startsWith("vp_") || "unknown".equalsIgnoreCase(id);
    }
}

package com.smartmeeting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.entity.MeetingSystemConfig;
import com.smartmeeting.exception.BusinessException;
import com.smartmeeting.repository.MeetingSystemConfigMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 工作台白名单授权服务。
 *
 * <p>从 {@code int_meeting_system_config} 表读取 {@code config_key=dashboard.user_grants} 的 JSON，
 * 解析为 {@link DashboardGrantConfig}，并在内存中缓存以支持高频查询。
 * 通过 {@link #reload()} 可由 internal reload API 热刷新，无需重启服务。
 *
 * <p>权限策略：
 * <ul>
 *   <li>{@code defaultDeny=true} 且不在 entries 中 → 403 拒绝</li>
 *   <li>在 entries 且 {@code enabled=true} → 可注册声纹（{@code canRegisterVoiceprint} 默认 true）</li>
 *   <li>{@code canCreateMeeting} / {@code canEndMeeting} 控制建会/结束会权限</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardGrantService {

    /** 存储在 int_meeting_system_config 表中的 config_key */
    public static final String CONFIG_KEY = "dashboard.user_grants";
    /** 配置分类 */
    public static final String CATEGORY = "dashboard";

    private final MeetingSystemConfigMapper configMapper;
    private final ObjectMapper objectMapper;

    private volatile DashboardGrantConfig cachedConfig;

    /**
     * 应用启动时加载白名单配置。
     */
    @PostConstruct
    public void init() {
        reload();
    }

    /**
     * 从数据库重新加载白名单配置到内存缓存。
     * <p>
     * 由 internal reload API 调用，实现热更新。
     */
    public synchronized void reload() {
        try {
            LambdaQueryWrapper<MeetingSystemConfig> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(MeetingSystemConfig::getConfigKey, CONFIG_KEY);
            MeetingSystemConfig row = configMapper.selectOne(wrapper);
            if (row == null || row.getValueJson() == null || row.getValueJson().isBlank()) {
                cachedConfig = DashboardGrantConfig.empty();
                log.info("dashboard grant config not found, using empty deny-all default");
                return;
            }
            cachedConfig = parseConfig(row.getValueJson());
            log.info("dashboard grant config reloaded: entries={}, defaultDeny={}",
                    cachedConfig.entries().size(), cachedConfig.defaultDeny());
        } catch (Exception e) {
            log.warn("dashboard grant config reload failed: {}", e.getMessage());
            if (cachedConfig == null) {
                cachedConfig = DashboardGrantConfig.empty();
            }
        }
    }

    /**
     * 查询指定飞书用户的授权条目。
     *
     * @param feishuUserId 飞书 user_id
     * @return 授权条目；未授权时返回 null
     */
    public GrantEntry findEntry(String feishuUserId) {
        if (feishuUserId == null || feishuUserId.isBlank()) {
            return null;
        }
        DashboardGrantConfig config = getEffectiveConfig();
        for (GrantEntry entry : config.entries()) {
            if (feishuUserId.equals(entry.feishuUserId())) {
                return entry;
            }
        }
        return null;
    }

    /**
     * 校验用户是否有权访问 Dashboard（任意权限即可进入）。
     *
     * @param feishuUserId 飞书 user_id
     * @throws BusinessException 403 不在白名单或已禁用
     */
    public void requireDashboardAccess(String feishuUserId) {
        GrantEntry entry = findEntry(feishuUserId);
        DashboardGrantConfig config = getEffectiveConfig();
        if (entry == null || !entry.enabled()) {
            if (config.defaultDeny()) {
                throw new BusinessException(403, "您未被授权访问会议管理前台，请联系管理员添加白名单");
            }
        }
    }

    /**
     * 校验用户是否有建会权限。
     *
     * @param feishuUserId 飞书 user_id
     * @throws BusinessException 403 无权限
     */
    public void requireCreateMeeting(String feishuUserId) {
        GrantEntry entry = findEntry(feishuUserId);
        if (entry == null || !entry.enabled() || !entry.canCreateMeeting()) {
            throw new BusinessException(403, "您没有创建会议的权限，请联系管理员");
        }
    }

    /**
     * 校验用户是否有结束会议权限。
     *
     * @param feishuUserId 飞书 user_id
     * @throws BusinessException 403 无权限
     */
    public void requireEndMeeting(String feishuUserId) {
        GrantEntry entry = findEntry(feishuUserId);
        if (entry == null || !entry.enabled() || !entry.canEndMeeting()) {
            throw new BusinessException(403, "您没有结束会议的权限，请联系管理员");
        }
    }

    /**
     * 校验用户是否可注册声纹（白名单内人人可注册）。
     *
     * @param feishuUserId 飞书 user_id
     * @throws BusinessException 403 无权限
     */
    public void requireVoiceprintRegistration(String feishuUserId) {
        GrantEntry entry = findEntry(feishuUserId);
        if (entry == null || !entry.enabled()) {
            DashboardGrantConfig config = getEffectiveConfig();
            if (config.defaultDeny()) {
                throw new BusinessException(403, "您未被授权注册声纹，请联系管理员");
            }
            return;
        }
        if (!entry.canRegisterVoiceprint()) {
            throw new BusinessException(403, "管理员已关闭您的声纹注册权限");
        }
    }

    /**
     * 构建当前用户的权限摘要（供 Dashboard {@code /me} 接口返回）。
     *
     * @param feishuUserId 飞书 user_id
     * @param userName     用户姓名
     * @return 权限摘要 DTO
     */
    public UserPermissions buildUserPermissions(String feishuUserId, String userName) {
        GrantEntry entry = findEntry(feishuUserId);
        boolean granted = entry != null && entry.enabled();
        return new UserPermissions(
                feishuUserId,
                userName != null ? userName : "",
                granted,
                granted && entry.canCreateMeeting(),
                granted && entry.canEndMeeting(),
                granted && entry.canRegisterVoiceprint()
        );
    }

    private DashboardGrantConfig getEffectiveConfig() {
        DashboardGrantConfig config = cachedConfig;
        return config != null ? config : DashboardGrantConfig.empty();
    }

    private DashboardGrantConfig parseConfig(String valueJson) {
        try {
            JsonNode root = objectMapper.readTree(valueJson);
            boolean defaultDeny = root.path("defaultDeny").asBoolean(true);
            JsonNode entriesNode = root.path("entries");
            List<GrantEntry> entries = new ArrayList<>();
            if (entriesNode.isArray()) {
                for (JsonNode n : entriesNode) {
                    String uid = n.path("feishuUserId").asText("").trim();
                    if (uid.isEmpty()) {
                        continue;
                    }
                    entries.add(new GrantEntry(
                            uid,
                            n.path("userName").asText("").trim(),
                            n.path("enabled").asBoolean(true),
                            n.path("canCreateMeeting").asBoolean(false),
                            n.path("canEndMeeting").asBoolean(false),
                            n.path("canRegisterVoiceprint").asBoolean(true),
                            n.path("remark").asText("").trim()
                    ));
                }
            }
            return new DashboardGrantConfig(defaultDeny, Collections.unmodifiableList(entries));
        } catch (Exception e) {
            log.warn("parse dashboard grant config failed: {}", e.getMessage());
            return DashboardGrantConfig.empty();
        }
    }

    /**
     * 白名单配置（从 int_meeting_system_config.value_json 解析）。
     *
     * @param defaultDeny 默认拒绝未授权用户
     * @param entries     授权条目列表（不可变）
     */
    public record DashboardGrantConfig(boolean defaultDeny, List<GrantEntry> entries) {
        /**
         * 空的拒绝所有配置。
         */
        public static DashboardGrantConfig empty() {
            return new DashboardGrantConfig(true, List.of());
        }
    }

    /**
     * 单条白名单授权条目。
     *
     * @param feishuUserId          飞书 user_id
     * @param userName              用户姓名（仅展示用）
     * @param enabled               是否启用
     * @param canCreateMeeting      是否可建会
     * @param canEndMeeting         是否可结束会
     * @param canRegisterVoiceprint 是否可注册声纹
     * @param remark                备注
     */
    public record GrantEntry(
            String feishuUserId,
            String userName,
            boolean enabled,
            boolean canCreateMeeting,
            boolean canEndMeeting,
            boolean canRegisterVoiceprint,
            String remark
    ) {
    }

    /**
     * 用户权限摘要（Dashboard {@code /me} 返回体）。
     *
     * @param feishuUserId          飞书 user_id
     * @param userName              用户姓名
     * @param granted               是否在白名单内且已启用
     * @param canCreateMeeting      是否可建会
     * @param canEndMeeting         是否可结束会
     * @param canRegisterVoiceprint 是否可注册声纹
     */
    public record UserPermissions(
            String feishuUserId,
            String userName,
            boolean granted,
            boolean canCreateMeeting,
            boolean canEndMeeting,
            boolean canRegisterVoiceprint
    ) {
    }
}

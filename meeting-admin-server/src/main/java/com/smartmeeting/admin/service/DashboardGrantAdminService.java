package com.smartmeeting.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.admin.api.dto.DashboardGrantConfigDto;
import com.smartmeeting.admin.api.dto.DashboardGrantEntryDto;
import com.smartmeeting.admin.api.dto.DashboardGrantPolicyDto;
import com.smartmeeting.admin.entity.MeetingSystemConfig;
import com.smartmeeting.admin.exception.BusinessException;
import com.smartmeeting.admin.repository.MeetingSystemConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Dashboard 授权管理业务逻辑（管理端）。
 *
 * <p>管理 int_meeting_system_config 表中 config_key='dashboard.user_grants' 的 JSON 配置。
 * 每次修改后自动调用 MeetingServerBridgeService 触发 meeting-server 重新加载 runtime config。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardGrantAdminService {

    private static final String CONFIG_KEY = "dashboard.user_grants";
    private static final String CATEGORY = "dashboard";

    private final MeetingSystemConfigMapper configMapper;
    private final ObjectMapper objectMapper;
    private final MeetingServerBridgeService bridgeService;

    public DashboardGrantConfigDto getConfig() {
        MeetingSystemConfig row = findByConfigKey();
        if (row == null || row.getValueJson() == null || row.getValueJson().isBlank()) {
            return emptyConfig();
        }
        return parseConfig(row.getValueJson());
    }

    public DashboardGrantPolicyDto getGrantPolicy() {
        DashboardGrantPolicyDto policy = new DashboardGrantPolicyDto();
        policy.setDefaultDeny(getConfig().isDefaultDeny());
        return policy;
    }

    @Transactional
    public DashboardGrantPolicyDto updateGrantPolicy(DashboardGrantPolicyDto policy) {
        if (policy == null) {
            throw new BusinessException("策略不能为空");
        }
        DashboardGrantConfigDto config = getConfig();
        config.setDefaultDeny(policy.isDefaultDeny());
        updateConfig(config);
        return getGrantPolicy();
    }

    @Transactional
    public DashboardGrantConfigDto updateConfig(DashboardGrantConfigDto config) {
        validateConfig(config);
        String json = serializeConfig(config);

        MeetingSystemConfig existing = findByConfigKey();
        if (existing == null) {
            MeetingSystemConfig row = new MeetingSystemConfig();
            row.setConfigKey(CONFIG_KEY);
            row.setCategory(CATEGORY);
            row.setValueJson(json);
            row.setDescription("Dashboard 用户授权配置");
            configMapper.insert(row);
        } else {
            existing.setValueJson(json);
            configMapper.updateById(existing);
        }

        triggerReload();
        return config;
    }

    @Transactional
    public DashboardGrantConfigDto addEntry(DashboardGrantEntryDto entry) {
        if (entry == null || entry.getFeishuUserId() == null || entry.getFeishuUserId().isBlank()) {
            throw new BusinessException("feishuUserId 不能为空");
        }

        DashboardGrantConfigDto config = getConfig();
        if (config.getEntries() == null) {
            config.setEntries(new ArrayList<>());
        }

        boolean exists = config.getEntries().stream()
                .anyMatch(e -> entry.getFeishuUserId().equals(e.getFeishuUserId()));
        if (exists) {
            throw new BusinessException("feishuUserId 已存在: " + entry.getFeishuUserId());
        }

        config.getEntries().add(entry);
        return updateConfig(config);
    }

    @Transactional
    public DashboardGrantConfigDto updateEntry(DashboardGrantEntryDto entry) {
        if (entry == null || entry.getFeishuUserId() == null || entry.getFeishuUserId().isBlank()) {
            throw new BusinessException("feishuUserId 不能为空");
        }

        DashboardGrantConfigDto config = getConfig();
        if (config.getEntries() == null) {
            throw new BusinessException("条目不存在: " + entry.getFeishuUserId());
        }

        boolean found = false;
        for (int i = 0; i < config.getEntries().size(); i++) {
            if (entry.getFeishuUserId().equals(config.getEntries().get(i).getFeishuUserId())) {
                config.getEntries().set(i, entry);
                found = true;
                break;
            }
        }

        if (!found) {
            throw new BusinessException("条目不存在: " + entry.getFeishuUserId());
        }

        return updateConfig(config);
    }

    @Transactional
    public DashboardGrantConfigDto deleteEntry(String feishuUserId) {
        if (feishuUserId == null || feishuUserId.isBlank()) {
            throw new BusinessException("feishuUserId 不能为空");
        }

        DashboardGrantConfigDto config = getConfig();
        if (config.getEntries() == null) {
            return config;
        }

        config.getEntries().removeIf(e -> feishuUserId.equals(e.getFeishuUserId()));
        return updateConfig(config);
    }

    public DashboardGrantEntryDto findEntry(String feishuUserId) {
        if (feishuUserId == null || feishuUserId.isBlank()) {
            return null;
        }
        DashboardGrantConfigDto config = getConfig();
        if (config.getEntries() == null) {
            return null;
        }
        for (DashboardGrantEntryDto entry : config.getEntries()) {
            if (feishuUserId.equals(entry.getFeishuUserId())) {
                return entry;
            }
        }
        return null;
    }

    public void triggerReload() {
        try {
            bridgeService.triggerRuntimeReload();
            log.info("dashboard-grants 配置已更新，已通知 meeting-server 重新加载");
        } catch (Exception e) {
            log.warn("触发 meeting-server reload 失败: {}", e.getMessage());
            throw new BusinessException("配置已保存，但通知 meeting-server 刷新失败: " + e.getMessage());
        }
    }

    private MeetingSystemConfig findByConfigKey() {
        LambdaQueryWrapper<MeetingSystemConfig> w = new LambdaQueryWrapper<>();
        w.eq(MeetingSystemConfig::getConfigKey, CONFIG_KEY);
        return configMapper.selectOne(w);
    }

    private DashboardGrantConfigDto emptyConfig() {
        DashboardGrantConfigDto config = new DashboardGrantConfigDto();
        config.setDefaultDeny(true);
        config.setEntries(new ArrayList<>());
        return config;
    }

    private DashboardGrantConfigDto parseConfig(String valueJson) {
        try {
            return objectMapper.readValue(valueJson, DashboardGrantConfigDto.class);
        } catch (Exception e) {
            log.warn("解析 dashboard-grants 配置失败: {}", e.getMessage());
            return emptyConfig();
        }
    }

    private String serializeConfig(DashboardGrantConfigDto config) {
        try {
            return objectMapper.writeValueAsString(config);
        } catch (Exception e) {
            log.error("序列化 dashboard-grants 配置失败: {}", e.getMessage());
            throw new BusinessException("配置序列化失败: " + e.getMessage());
        }
    }

    private void validateConfig(DashboardGrantConfigDto config) {
        if (config == null) {
            throw new BusinessException("配置不能为空");
        }
        if (config.getEntries() != null) {
            for (DashboardGrantEntryDto entry : config.getEntries()) {
                if (entry.getFeishuUserId() == null || entry.getFeishuUserId().isBlank()) {
                    throw new BusinessException("授权条目中 feishuUserId 不能为空");
                }
            }
        }
    }
}

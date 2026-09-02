package com.smartmeeting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.config.MeetingFeishuMinutesProperties;
import com.smartmeeting.entity.MeetingSystemConfig;
import com.smartmeeting.repository.MeetingSystemConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 飞书妙记 OAuth {@code refresh_token} 轮换后写入 {@code int_meeting_system_config}，
 * 避免单次使用后旧 token 仍留在 DB 导致下次刷新 20026。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeishuMinutesRefreshTokenPersister {

    static final String CONFIG_KEY = "meeting.feishu.minutes.user-refresh-token";

    private final MeetingSystemConfigMapper configMapper;
    private final MeetingFeishuMinutesProperties minutesProperties;
    private final ObjectMapper objectMapper;

    /**
     * 若 {@code newRefreshToken} 与当前内存值不同，则 upsert DB 并更新 {@link MeetingFeishuMinutesProperties}。
     *
     * @param newRefreshToken 飞书 refresh 响应中的最新 refresh_token
     */
    public void persistIfRotated(String newRefreshToken) {
        if (newRefreshToken == null || newRefreshToken.isBlank()) {
            return;
        }
        String current = trim(minutesProperties.getUserRefreshToken());
        if (newRefreshToken.equals(current)) {
            return;
        }
        try {
            String valueJson = objectMapper.writeValueAsString(newRefreshToken);
            upsertConfig(valueJson);
            minutesProperties.setUserRefreshToken(newRefreshToken);
            log.info("FeishuMinutes user refresh_token rotated and persisted to system config");
        } catch (Exception e) {
            log.warn("FeishuMinutes persist refresh_token failed: {}", e.getMessage());
        }
    }

    private void upsertConfig(String valueJson) {
        MeetingSystemConfig row = configMapper.selectOne(new LambdaQueryWrapper<MeetingSystemConfig>()
                .eq(MeetingSystemConfig::getConfigKey, CONFIG_KEY)
                .last("LIMIT 1"));
        if (row == null) {
            row = new MeetingSystemConfig();
            row.setConfigKey(CONFIG_KEY);
            row.setCategory("vc");
            row.setDescription("妙记 /media 下载 OAuth refresh_token（meeting-server 自动刷新并轮换持久化）");
            row.setValueJson(valueJson);
            row.setUpdatedAt(LocalDateTime.now());
            configMapper.insert(row);
        } else {
            row.setValueJson(valueJson);
            row.setCategory("vc");
            row.setUpdatedAt(LocalDateTime.now());
            configMapper.updateById(row);
        }
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}

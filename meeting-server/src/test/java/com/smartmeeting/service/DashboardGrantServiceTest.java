package com.smartmeeting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.entity.MeetingSystemConfig;
import com.smartmeeting.exception.BusinessException;
import com.smartmeeting.repository.MeetingSystemConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@link DashboardGrantService} 白名单解析与权限校验单元测试。
 */
@ExtendWith(MockitoExtension.class)
class DashboardGrantServiceTest {

    private static final String GRANTED_USER = "ou-granted";
    private static final String LIMITED_USER = "ou-limited";

    @Mock
    private MeetingSystemConfigMapper configMapper;

    private DashboardGrantService service;

    @BeforeEach
    void setUp() {
        service = new DashboardGrantService(configMapper, new ObjectMapper());
        stubConfig("""
                {
                  "defaultDeny": true,
                  "entries": [
                    {
                      "feishuUserId": "%s",
                      "userName": "全权限",
                      "enabled": true,
                      "canCreateMeeting": true,
                      "canEndMeeting": true,
                      "canRegisterVoiceprint": true
                    },
                    {
                      "feishuUserId": "%s",
                      "userName": "只读",
                      "enabled": true,
                      "canCreateMeeting": false,
                      "canEndMeeting": false,
                      "canRegisterVoiceprint": true
                    }
                  ]
                }
                """.formatted(GRANTED_USER, LIMITED_USER));
        service.reload();
    }

    @Test
    @DisplayName("白名单内用户可进入 Dashboard")
    void grantedUser_canAccessDashboard() {
        assertDoesNotThrow(() -> service.requireDashboardAccess(GRANTED_USER));
    }

    @Test
    @DisplayName("未授权用户访问 Dashboard → 403")
    void unknownUser_denied() {
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> service.requireDashboardAccess("ou-unknown"));

        assertEquals(403, ex.getCode());
    }

    @Test
    @DisplayName("canCreateMeeting=false 时建会被拒绝")
    void limitedUser_cannotCreateMeeting() {
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> service.requireCreateMeeting(LIMITED_USER));

        assertEquals(403, ex.getCode());
    }

    @Test
    @DisplayName("buildUserPermissions 返回细粒度权限")
    void buildUserPermissions_reflectsFlags() {
        DashboardGrantService.UserPermissions full =
                service.buildUserPermissions(GRANTED_USER, "全权限");
        assertTrue(full.granted());
        assertTrue(full.canCreateMeeting());
        assertTrue(full.canEndMeeting());
        assertTrue(full.canRegisterVoiceprint());

        DashboardGrantService.UserPermissions limited =
                service.buildUserPermissions(LIMITED_USER, "只读");
        assertTrue(limited.granted());
        assertFalse(limited.canCreateMeeting());
        assertFalse(limited.canEndMeeting());
        assertTrue(limited.canRegisterVoiceprint());
    }

    @Test
    @DisplayName("配置缺失时使用 defaultDeny 空列表")
    void missingConfig_deniesAll() {
        when(configMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        service.reload();

        assertThrows(BusinessException.class, () -> service.requireDashboardAccess(GRANTED_USER));
    }

    private void stubConfig(String json) {
        MeetingSystemConfig row = new MeetingSystemConfig();
        row.setConfigKey(DashboardGrantService.CONFIG_KEY);
        row.setValueJson(json);
        when(configMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(row);
    }
}

package com.smartmeeting.config;

import com.smartmeeting.entity.MatterProgressDocConfig;
import com.smartmeeting.repository.MatterProgressDocConfigMapper;
import com.smartmeeting.repository.MeetingMinuteMapper;
import com.smartmeeting.service.PresetAgendaDocService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DatabaseSeedStartupValidator} 单元测试：验证应用启动时种子数据校验逻辑。
 */
@ExtendWith(MockitoExtension.class)
class DatabaseSeedStartupValidatorTest {

    @Mock
    private MatterProgressDocConfigMapper docConfigMapper;

    @Mock
    private MeetingMinuteMapper meetingMinuteMapper;
    @Mock
    private PresetAgendaDocService presetAgendaDocService;

    private DatabaseSeedStartupValidator validator;

    /** 构造 Mock 环境并初始化校验器实例。 */
    @BeforeEach
    void setUp() {
        Environment env = new MockEnvironment()
                .withProperty("spring.profiles.active", "dev")
                .withProperty("spring.sql.init.mode", "never");
        MeetingDatabaseProperties databaseProperties = new MeetingDatabaseProperties();
        validator = new DatabaseSeedStartupValidator(env, databaseProperties, docConfigMapper,
                meetingMinuteMapper, presetAgendaDocService);
        when(meetingMinuteMapper.selectCount(any())).thenReturn(0L);
        when(presetAgendaDocService.openclawBriefingIneligibleReason(nullable(Integer.class), anyInt()))
                .thenReturn("openclaw_briefing 未为 1");
    }

    /** 启动校验应仅从数据库读取配置，不执行写入。 */
    @Test
    void validateOnStartup_readsDbOnly() {
        MatterProgressDocConfig c = new MatterProgressDocConfig();
        c.setPresetTypeCode(1);
        c.setAgendaIndex(1);
        c.setEnabled(1);
        c.setFeishuDocUrl("https://ovjde0k7vc1.feishu.cn/docx/doxTest123");
        when(docConfigMapper.selectList(any())).thenReturn(List.of(c));
        validator.validateOnStartup();
        verify(docConfigMapper).selectList(any());
    }
}

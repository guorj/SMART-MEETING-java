package com.smartmeeting.config;

import com.smartmeeting.entity.MatterProgressDocConfig;
import com.smartmeeting.repository.MatterProgressDocConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatabaseSeedStartupValidatorTest {

    @Mock
    private MatterProgressDocConfigMapper docConfigMapper;

    private DatabaseSeedStartupValidator validator;

    @BeforeEach
    void setUp() {
        Environment env = new MockEnvironment()
                .withProperty("spring.profiles.active", "dev")
                .withProperty("spring.sql.init.mode", "never");
        MeetingDatabaseProperties databaseProperties = new MeetingDatabaseProperties();
        validator = new DatabaseSeedStartupValidator(env, databaseProperties, docConfigMapper);
    }

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

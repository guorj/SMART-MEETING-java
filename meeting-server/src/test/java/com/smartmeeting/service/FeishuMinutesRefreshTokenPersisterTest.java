package com.smartmeeting.service;

import com.smartmeeting.config.MeetingFeishuMinutesProperties;
import com.smartmeeting.entity.MeetingSystemConfig;
import com.smartmeeting.repository.MeetingSystemConfigMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeishuMinutesRefreshTokenPersisterTest {

    @Mock
    private MeetingSystemConfigMapper configMapper;

    private MeetingFeishuMinutesProperties minutesProperties;
    private FeishuMinutesRefreshTokenPersister persister;

    @BeforeEach
    void setUp() {
        minutesProperties = new MeetingFeishuMinutesProperties();
        minutesProperties.setUserRefreshToken("old-refresh");
        persister = new FeishuMinutesRefreshTokenPersister(
                configMapper, minutesProperties, new ObjectMapper());
    }

    @Test
    void persistIfRotated_skipsWhenUnchanged() {
        persister.persistIfRotated("old-refresh");
        verify(configMapper, never()).insert(any());
        verify(configMapper, never()).updateById(any());
    }

    @Test
    void persistIfRotated_updatesDbAndMemoryWhenRotated() {
        MeetingSystemConfig existing = new MeetingSystemConfig();
        existing.setId(1L);
        existing.setConfigKey(FeishuMinutesRefreshTokenPersister.CONFIG_KEY);
        when(configMapper.selectOne(any())).thenReturn(existing);

        persister.persistIfRotated("new-refresh");

        ArgumentCaptor<MeetingSystemConfig> captor = ArgumentCaptor.forClass(MeetingSystemConfig.class);
        verify(configMapper).updateById(captor.capture());
        assertEquals("\"new-refresh\"", captor.getValue().getValueJson());
        assertEquals("new-refresh", minutesProperties.getUserRefreshToken());
    }
}

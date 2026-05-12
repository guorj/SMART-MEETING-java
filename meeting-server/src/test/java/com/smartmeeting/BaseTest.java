package com.smartmeeting;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import com.smartmeeting.repository.*;

@SpringBootTest
@AutoConfigureMockMvc
/** dev：真实业务配置（见 application-dev.yml）；test：仅覆写 SQL 初始化与测试目录（见 application-test.yml） */
@ActiveProfiles({"dev", "test"})
public abstract class BaseTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected MeetingMapper meetingMapper;

    @Autowired
    protected ParticipantMapper participantMapper;

    @Autowired
    protected TodoMapper todoMapper;

    @Autowired
    protected TranscriptMapper transcriptMapper;

    @BeforeEach
    void setUp() {
        transcriptMapper.delete(null);
        todoMapper.delete(null);
        participantMapper.delete(null);
        meetingMapper.delete(null);
    }
}

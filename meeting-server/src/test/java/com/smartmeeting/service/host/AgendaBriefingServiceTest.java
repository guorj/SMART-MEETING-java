package com.smartmeeting.service.host;

import com.smartmeeting.entity.Meeting;
import com.smartmeeting.repository.MeetingMapper;
import com.smartmeeting.service.AiAgentService;
import com.smartmeeting.service.BitableDirectiveBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgendaBriefingServiceTest {

    @Mock
    private MeetingMapper meetingMapper;
    @Mock
    private AiAgentService aiAgentService;
    @Mock
    private BitableDirectiveBuilder bitableDirectiveBuilder;

    @InjectMocks
    private AgendaBriefingService agendaBriefingService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(agendaBriefingService, "enabled", true);
    }

    @Test
    @DisplayName("OpenClaw 成功时返回 openclaw_gateway 来源")
    void generateBriefing_openclawSuccess() {
        Meeting meeting = new Meeting();
        meeting.setId("m-1");
        meeting.setTitle("综合管理会");
        when(meetingMapper.selectById("m-1")).thenReturn(meeting);
        String url = "https://ovjde0k7vc1.feishu.cn/base/abc?table=tbl1&view=vew1";
        when(bitableDirectiveBuilder.buildDirectiveForHostAgenda("会序2", url, "BASE"))
                .thenReturn("directive");
        when(aiAgentService.isAgentAvailable()).thenReturn(true);
        when(aiAgentService.runAgendaBriefingViaOpenclaw(meeting, "directive", url, "会序2"))
                .thenReturn("# 通报\n\n内容");

        AgendaBriefingResult result = agendaBriefingService.generateBriefing("m-1", "会序2", url, "BASE");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getSource()).isEqualTo("openclaw_gateway");
        assertThat(result.getMarkdown()).contains("通报");
    }

    @Test
    @DisplayName("Gateway 不可用时失败，不兜底 LLM")
    void generateBriefing_gatewayUnavailable() {
        Meeting meeting = new Meeting();
        meeting.setId("m-4");
        when(meetingMapper.selectById("m-4")).thenReturn(meeting);
        String url = "https://ovjde0k7vc1.feishu.cn/base/abc?table=tbl1&view=vew1";
        when(bitableDirectiveBuilder.buildDirectiveForHostAgenda("会序2", url, "BASE"))
                .thenReturn("directive");
        when(aiAgentService.isAgentAvailable()).thenReturn(false);

        AgendaBriefingResult result = agendaBriefingService.generateBriefing("m-4", "会序2", url, "BASE");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("OpenClaw");
        verify(aiAgentService, never()).runAgendaBriefingViaOpenclaw(any(), any(), any(), any());
    }

    @Test
    @DisplayName("未配置飞书 URL 时失败")
    void generateBriefing_noUrl() {
        Meeting meeting = new Meeting();
        meeting.setId("m-2");
        when(meetingMapper.selectById("m-2")).thenReturn(meeting);

        AgendaBriefingResult result = agendaBriefingService.generateBriefing("m-2", "会序1", "", "BASE");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("feishu_doc_url");
        verify(aiAgentService, never()).runAgendaBriefingViaOpenclaw(any(), any(), any(), any());
    }

    @Test
    @DisplayName("功能关闭时返回 disabled")
    void generateBriefing_disabled() {
        ReflectionTestUtils.setField(agendaBriefingService, "enabled", false);

        AgendaBriefingResult result = agendaBriefingService.generateBriefing(
                "m-3", "会序1", "https://x.feishu.cn/base/a?table=t1", "BASE");

        assertThat(result.getSource()).isEqualTo("disabled");
        verify(meetingMapper, never()).selectById(any());
    }
}

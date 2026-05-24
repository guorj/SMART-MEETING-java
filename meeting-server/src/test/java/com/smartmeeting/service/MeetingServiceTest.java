package com.smartmeeting.service;

import com.smartmeeting.BaseTest;
import com.smartmeeting.api.dto.*;
import com.smartmeeting.api.dto.host.HostAgendaItemDto;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.enums.MeetingStatus;
import com.smartmeeting.exception.BusinessException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * {@link MeetingService} 集成测试：覆盖会议创建、启动、结束、查询及 host_agenda 拆分。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MeetingServiceTest extends BaseTest {

    @Autowired
    private MeetingService meetingService;

    /** 有上次会议时 startMeeting 会推飞书卡片；即使用 dev 真凭据也不应对外发请求，必须 Mock */
    @MockBean
    private FeishuService feishuService;

    /** Mock 飞书卡片发送，避免测试期间对外发请求。 */
    @BeforeEach
    void stubFeishuCard() {
        when(feishuService.sendCardMessage(anyString(), anyString(), anyList())).thenReturn(true);
    }

    /** 创建会议应写入基本信息并返回 ISSUE_COLLECTING 状态。 */
    @Test
    @Order(1)
    @DisplayName("F-MID-01: 创建会议")
    void testCreateMeeting() {
        MeetingCreateRequest request = new MeetingCreateRequest();
        request.setTitle("测试会议");
        request.setCompany("集团总部");
        request.setGroupName("测试组");
        request.setAgenda(List.of("议题1", "议题2"));

        MeetingCreateRequest.ParticipantEntry p = new MeetingCreateRequest.ParticipantEntry();
        p.setUserId("user_001");
        p.setName("张三");
        request.setParticipants(List.of(p));

        MeetingResponse response = meetingService.createMeeting(request);

        assertNotNull(response.getId());
        assertEquals("测试会议", response.getTitle());
        assertEquals("集团总部", response.getCompany());
        assertEquals(MeetingStatus.ISSUE_COLLECTING.name(), response.getStatus());
        assertEquals(1, response.getParticipants().size());
        assertEquals("张三", response.getParticipants().get(0).getName());
    }

    /** 无上次会议时启动应进入 STARTED 状态。 */
    @Test
    @Order(2)
    @DisplayName("F-MID-02: 启动会议（无上次会议）")
    void testStartMeetingWithoutPrevious() {
        MeetingResponse created = createTestMeeting("测试会议", null);
        MeetingResponse started = meetingService.startMeeting(created.getId());

        assertEquals(MeetingStatus.STARTED.name(), started.getStatus());
        assertNotNull(started.getActualStartTime());
    }

    /** 有上次会议时启动也应进入 STARTED（会前进度由 bot 定时生成，建会不再推卡片）。 */
    @Test
    @Order(3)
    @DisplayName("F-MID-02: 启动会议（有上次会议 → STARTED，不再 REVIEWING）")
    void testStartMeetingWithPrevious() {
        MeetingResponse previous = createTestMeeting("上次会议", null);
        meetingService.startMeeting(previous.getId());

        MeetingCreateRequest request = new MeetingCreateRequest();
        request.setTitle("当前会议");
        request.setCompany("集团总部");
        request.setGroupName("测试组");
        request.setPreviousMeetingId(previous.getId());

        MeetingResponse current = meetingService.createMeeting(request);
        MeetingResponse started = meetingService.startMeeting(current.getId());

        assertEquals(MeetingStatus.STARTED.name(), started.getStatus());
        assertEquals(previous.getId(), started.getPreviousMeetingId());
    }

    /** 结束会议应进入 PROCESSING 并记录实际结束时间与时长。 */
    @Test
    @Order(4)
    @DisplayName("F-MID-03: 结束会议")
    void testEndMeeting() {
        MeetingResponse created = createTestMeeting("测试会议", null);
        meetingService.startMeeting(created.getId());

        MeetingResponse ended = meetingService.endMeeting(created.getId());

        assertEquals(MeetingStatus.PROCESSING.name(), ended.getStatus());
        assertNotNull(ended.getActualEndTime());
        assertTrue(ended.getDurationSeconds() >= 0);
    }

    /** 按 ID 查询应返回完整会议详情。 */
    @Test
    @Order(5)
    @DisplayName("F-MID-04: 查询会议详情")
    void testGetMeeting() {
        MeetingResponse created = createTestMeeting("测试会议", null);
        MeetingResponse detail = meetingService.getMeeting(created.getId());

        assertEquals(created.getId(), detail.getId());
        assertEquals("测试会议", detail.getTitle());
    }

    /** 查询不存在的会议应抛出 BusinessException。 */
    @Test
    @Order(6)
    @DisplayName("F-MID-04: 查询不存在的会议")
    void testGetMeetingNotFound() {
        assertThrows(BusinessException.class, () ->
                meetingService.getMeeting("non-existent-id"));
    }

    /** 列表查询应返回多条会议记录。 */
    @Test
    @Order(7)
    @DisplayName("F-MID-05: 查询会议列表")
    void testListMeetings() {
        createTestMeeting("会议1", null);
        createTestMeeting("会议2", null);

        List<MeetingResponse> list = meetingService.listMeetings(null, null, 0, 10);
        assertTrue(list.size() >= 2);
    }

    /** 按状态筛选列表应只返回匹配状态的会议。 */
    @Test
    @Order(8)
    @DisplayName("F-MID-05: 按状态筛选会议列表")
    void testListMeetingsByStatus() {
        MeetingResponse m1 = createTestMeeting("会议1", null);
        meetingService.startMeeting(m1.getId());

        List<MeetingResponse> started = meetingService.listMeetings("STARTED", null, 0, 10);
        assertTrue(started.size() >= 1);
        assertEquals("STARTED", started.get(0).getStatus());
    }

    /** 启动不存在的会议应抛出 BusinessException。 */
    @Test
    @Order(9)
    @DisplayName("异常测试: 启动不存在的会议")
    void testStartNonExistentMeeting() {
        assertThrows(BusinessException.class, () ->
                meetingService.startMeeting("non-existent-id"));
    }

    /** 建会时 host_agenda 与 agenda 应拆分写入并在详情中正确回读。 */
    @Test
    @Order(10)
    @DisplayName("host_agenda 与 agenda 拆分：建会写入并详情回读")
    void testCreateMeetingWithHostAgendaItems() {
        MeetingCreateRequest request = new MeetingCreateRequest();
        request.setTitle("主持拆分测");
        request.setCompany("集团总部");
        request.setGroupName("测试组");
        request.setAgenda(List.of("会务行1", "会务行2"));
        HostAgendaItemDto a = new HostAgendaItemDto();
        a.setTitle("主持议题A");
        a.setMinutes(3);
        HostAgendaItemDto b = new HostAgendaItemDto();
        b.setTitle("事项进度通报");
        b.setMinutes(7);
        request.setHostAgendaItems(List.of(a, b));

        MeetingResponse created = meetingService.createMeeting(request);
        Meeting row = meetingMapper.selectById(created.getId());
        assertNotNull(row.getHostAgenda());
        assertTrue(row.getHostAgenda().contains("\"items\""));
        assertTrue(row.getHostAgenda().contains("主持议题A"));

        MeetingResponse detail = meetingService.getMeeting(created.getId());
        assertEquals(List.of("会务行1", "会务行2"), detail.getAgenda());
        assertNotNull(detail.getHostAgendaItems());
        assertEquals(2, detail.getHostAgendaItems().size());
        assertEquals("主持议题A", detail.getHostAgendaItems().get(0).getTitle());
        assertEquals(3, detail.getHostAgendaItems().get(0).getMinutes());
        assertEquals("事项进度通报", detail.getHostAgendaItems().get(1).getTitle());
        assertEquals(7, detail.getHostAgendaItems().get(1).getMinutes());
    }

    private MeetingResponse createTestMeeting(String title, String previousId) {
        MeetingCreateRequest request = new MeetingCreateRequest();
        request.setTitle(title);
        request.setCompany("集团总部");
        request.setGroupName("测试组");
        if (previousId != null) {
            request.setPreviousMeetingId(previousId);
        }
        return meetingService.createMeeting(request);
    }
}

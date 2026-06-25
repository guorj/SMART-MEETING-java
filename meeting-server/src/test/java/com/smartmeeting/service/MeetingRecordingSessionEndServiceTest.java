package com.smartmeeting.service;

import com.smartmeeting.api.dto.MeetingResponse;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.enums.MeetingStatus;
import com.smartmeeting.exception.BusinessException;
import com.smartmeeting.repository.MeetingMapper;
import com.smartmeeting.service.host.MeetingHostMediaTeardownService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * {@link MeetingRecordingSessionEndService} 单元测试：验证录音页结束会议时的 teardown 顺序与分支逻辑。
 */
@ExtendWith(MockitoExtension.class)
class MeetingRecordingSessionEndServiceTest {

    @Mock
    private MeetingMapper meetingMapper;
    @Mock
    private RecordingService recordingService;
    @Mock
    private MeetingService meetingService;
    @Mock
    private FeishuService feishuService;
    @Mock
    private FeishuCardBuilder cardBuilder;
    @Mock
    private MeetingHostMediaTeardownService meetingHostMediaTeardownService;

    @InjectMocks
    private MeetingRecordingSessionEndService service;

    /** 结束会议时应先执行 teardown，再查询数据库。 */
    @Test
    @DisplayName("结束会议：始终先 teardown，再查库")
    void teardownRunsBeforeMapperSelect() {
        String id = "meet-1";
        Meeting m = meeting(id, MeetingStatus.COMPLETED.name());
        when(meetingMapper.selectById(id)).thenReturn(m);
        MeetingResponse expected = new MeetingResponse();
        expected.setId(id);
        when(meetingService.getMeeting(id)).thenReturn(expected);

        MeetingResponse out = service.endFromRecordingPage(id);

        assertSame(expected, out);
        InOrder order = inOrder(meetingHostMediaTeardownService, meetingMapper);
        order.verify(meetingHostMediaTeardownService).beforeRecordingSessionEnd(id);
        order.verify(meetingMapper).selectById(id);
    }

    /** 会议不存在时应抛出 404，但 teardown 仍会执行。 */
    @Test
    @DisplayName("会议不存在：返回 404（teardown 仍会执行）")
    void meetingNotFound_throws404() {
        String id = "missing";
        when(meetingMapper.selectById(id)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.endFromRecordingPage(id));

        assertEquals(404, ex.getCode());
        verify(meetingHostMediaTeardownService).beforeRecordingSessionEnd(id);
        verify(recordingService, never()).stopRecording(anyString());
    }

    /** RECORDING 且有内存会话时应调用 stopRecording。 */
    @Test
    @DisplayName("RECORDING 且有内存会话：stopRecording")
    void recordingWithSession_stopsRecording() {
        String id = "meet-r";
        Meeting m = meeting(id, MeetingStatus.RECORDING.name());
        m.setChatId(null);
        when(meetingMapper.selectById(id)).thenReturn(m);
        when(recordingService.getRecordingState(id)).thenReturn(RecordingService.RecordingState.RECORDING);
        MeetingResponse after = new MeetingResponse();
        after.setId(id);
        after.setStatus(MeetingStatus.PROCESSING.name());
        when(meetingService.getMeeting(id)).thenReturn(after);

        MeetingResponse out = service.endFromRecordingPage(id);

        assertEquals(MeetingStatus.PROCESSING.name(), out.getStatus());
        verify(recordingService).stopRecording(id);
        verify(meetingService, never()).endMeeting(anyString());
    }

    /** RECORDING 但无内存会话时应回退调用 endMeeting。 */
    @Test
    @DisplayName("RECORDING 但无内存会话：回退 endMeeting")
    void recordingWithoutMemorySession_fallsBackToEndMeeting() {
        String id = "meet-r2";
        Meeting m = meeting(id, MeetingStatus.RECORDING.name());
        m.setChatId(null);
        when(meetingMapper.selectById(id)).thenReturn(m);
        when(recordingService.getRecordingState(id)).thenReturn(null);
        MeetingResponse ended = new MeetingResponse();
        ended.setId(id);
        ended.setStatus(MeetingStatus.PROCESSING.name());
        when(meetingService.endMeeting(id)).thenReturn(ended);

        MeetingResponse out = service.endFromRecordingPage(id);

        assertSame(ended, out);
        verify(recordingService, never()).stopRecording(anyString());
        verify(meetingService).endMeeting(id);
    }

    /** ISSUE_COLLECTING 草稿：清理录音态并取消预约。 */
    @Test
    @DisplayName("ISSUE_COLLECTING：取消草稿会议")
    void issueCollecting_cancelsDraft() {
        String id = "meet-draft";
        Meeting m = meeting(id, MeetingStatus.ISSUE_COLLECTING.name());
        when(meetingMapper.selectById(id)).thenReturn(m);
        MeetingResponse cancelled = new MeetingResponse();
        cancelled.setId(id);
        cancelled.setStatus(MeetingStatus.CANCELLED.name());
        when(meetingService.cancelDraftMeeting(id)).thenReturn(cancelled);

        MeetingResponse out = service.endFromRecordingPage(id);

        assertEquals(MeetingStatus.CANCELLED.name(), out.getStatus());
        verify(recordingService).clearRecordingState(id);
        verify(meetingService).cancelDraftMeeting(id);
        verify(meetingService, never()).endMeeting(anyString());
    }

    private static Meeting meeting(String id, String status) {
        Meeting m = new Meeting();
        m.setId(id);
        m.setStatus(status);
        return m;
    }
}

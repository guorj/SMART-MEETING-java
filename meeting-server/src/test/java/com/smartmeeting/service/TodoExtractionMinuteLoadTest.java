package com.smartmeeting.service;

import com.smartmeeting.BaseTest;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.enums.MeetingStatus;
import com.smartmeeting.enums.MinuteGenerationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link TodoExtractionService} 集成测试：验证待办提取时从数据库加载纪要正文。
 */
class TodoExtractionMinuteLoadTest extends BaseTest {

    @Autowired
    private TodoExtractionService todoExtractionService;

    @Autowired
    private MeetingMinuteService meetingMinuteService;

    /** extractTodos 在文本为空时应从数据库加载纪要正文且不抛异常。 */
    @Test
    @DisplayName("extractTodos(meetingId) 从库加载纪要正文")
    void extractTodosLoadsFromDatabaseWhenTextEmpty() {
        Meeting m = new Meeting();
        m.setId(java.util.UUID.randomUUID().toString());
        m.setTitle("待办加载测试");
        m.setCompany("集团");
        m.setGroupName("组");
        m.setStatus(MeetingStatus.COMPLETED.name());
        m.setCreatorId("u1");
        m.setCreatedAt(LocalDateTime.now());
        m.setUpdatedAt(LocalDateTime.now());
        meetingMapper.insert(m);

        meetingMinuteService.saveLatest(m.getId(), "无待办内容的纪要", MinuteGenerationStatus.READY);

        assertDoesNotThrow(() -> todoExtractionService.extractTodos(m.getId()));
    }
}

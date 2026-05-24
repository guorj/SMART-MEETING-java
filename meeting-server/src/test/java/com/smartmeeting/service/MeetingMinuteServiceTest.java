package com.smartmeeting.service;

import com.smartmeeting.BaseTest;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.enums.MeetingStatus;
import com.smartmeeting.enums.MinuteGenerationStatus;
import com.smartmeeting.repository.MeetingMinuteMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link MeetingMinuteService} 集成测试：验证纪要 upsert 与读取逻辑。
 */
class MeetingMinuteServiceTest extends BaseTest {

    @Autowired
    private MeetingMinuteService meetingMinuteService;

    @Autowired
    private MeetingMinuteMapper meetingMinuteMapper;

    /** saveLatest 应 upsert 纪要正文，getLatest 与 getContentMarkdownOrEmpty 可读回。 */
    @Test
    @DisplayName("saveLatest upsert 并 getLatest 可读")
    void saveAndLoad() {
        Meeting m = new Meeting();
        m.setId(java.util.UUID.randomUUID().toString());
        m.setTitle("纪要持久化测试");
        m.setCompany("测试公司");
        m.setGroupName("测试组");
        m.setPresetTypeCode(1);
        m.setStatus(MeetingStatus.COMPLETED.name());
        m.setCreatorId("test-user");
        m.setCreatedAt(LocalDateTime.now());
        m.setUpdatedAt(LocalDateTime.now());
        meetingMapper.insert(m);

        String body = "# 测试纪要\n\n- 决议 A";
        meetingMinuteService.saveLatest(m.getId(), body, MinuteGenerationStatus.READY);

        var loaded = meetingMinuteService.getLatest(m.getId());
        assertTrue(loaded.isPresent());
        assertEquals(body, loaded.get().getContentMarkdown());
        assertEquals(1, loaded.get().getPresetTypeCode());
        assertEquals(MinuteGenerationStatus.READY.name(), loaded.get().getGenerationStatus());

        String updated = body + "\n\n追加段落";
        meetingMinuteService.saveLatest(m.getId(), updated, MinuteGenerationStatus.PARTIAL);
        assertEquals(updated, meetingMinuteService.getContentMarkdownOrEmpty(m.getId()));
        assertEquals(MinuteGenerationStatus.PARTIAL.name(),
                meetingMinuteService.getLatest(m.getId()).get().getGenerationStatus());

        String feishuUrl = "https://example.feishu.cn/docx/abc123";
        meetingMinuteService.updateContentUrl(m.getId(), feishuUrl);
        assertEquals(feishuUrl, meetingMinuteService.getLatest(m.getId()).get().getContentUrl());

        meetingMinuteService.saveLatest(m.getId(), updated, MinuteGenerationStatus.READY,
                "https://example.feishu.cn/docx/new");
        assertEquals("https://example.feishu.cn/docx/new",
                meetingMinuteService.getLatest(m.getId()).get().getContentUrl());
    }
}

package com.smartmeeting.api.dto;

import com.smartmeeting.api.dto.host.HostAgendaItemDto;
import jakarta.validation.constraints.AssertTrue;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class MeetingCreateRequest {
    /** 会议主题；使用 presetTypeCode 1-5 时可由服务端覆盖 */
    private String title;
    private List<String> agenda;
    /** AI 主持议题；写入 int_meeting.host_agenda（与会务 agenda 分离） */
    private List<HostAgendaItemDto> hostAgendaItems;
    /** 所属集团；预设 1-5 时由服务端覆盖 */
    private String company;
    private String department;
    /** 会议组；预设 1-5 时由服务端覆盖 */
    private String groupName;
    private String creatorId;
    private String chatId;
    private String roomId;
    private String previousMeetingId;
    private LocalDateTime scheduledTime;
    private List<ParticipantEntry> participants;

    /**
     * 1-5：固定会务（库表 int_meeting_type_preset）；6：其他会议（须填 title）；null：完全自定义（须填 title、company、groupName）
     */
    private Integer presetTypeCode;

    @Data
    public static class ParticipantEntry {
        private String userId;
        private String name;
    }

    @AssertTrue(message = "请选择 presetTypeCode 1-5，或选择 6 并填写会议主题，或填写完整自定义会议（主题、集团、会议组）")
    public boolean isValidPresetOrManual() {
        Integer p = presetTypeCode;
        if (p != null && p >= 1 && p <= 5) {
            return true;
        }
        if (p != null && p == 6) {
            return title != null && !title.trim().isEmpty();
        }
        if (p != null) {
            return false;
        }
        return title != null && !title.trim().isEmpty()
                && company != null && !company.trim().isEmpty()
                && groupName != null && !groupName.trim().isEmpty();
    }
}

package com.smartmeeting.api.dto;

import com.smartmeeting.api.dto.host.HostAgendaItemDto;
import jakarta.validation.constraints.AssertTrue;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 创建会议请求体（{@code POST /api/v1/meetings}）。
 * <p>
 * 支持三种模式：预设类型 1–5（库表 {@code int_meeting_type_preset}）、
 * 第 6 类「其他会议」（须填 title）、或完全自定义（title + company + groupName）。
 *
 * @see MeetingResponse
 */
@Data
public class MeetingCreateRequest {
    /** 会议主题；使用 presetTypeCode 1-5 时可由服务端覆盖 */
    private String title;
    /** 会务议程条目列表（与会务展示用，与 AI 主持议程分离） */
    private List<String> agenda;
    /** AI 主持议题；写入 int_meeting.host_agenda（与会务 agenda 分离） */
    private List<HostAgendaItemDto> hostAgendaItems;
    /** 所属集团；预设 1-5 时由服务端覆盖 */
    private String company;
    private String department;
    /** 会议组；预设 1-5 时由服务端覆盖 */
    private String groupName;
    private String creatorId;
    /** 飞书会话 chat_id，用于推送卡片 */
    private String chatId;
    private String roomId;
    /** 链路上次会议 ID，用于待办进度通报 */
    private String previousMeetingId;
    private LocalDateTime scheduledTime;
    private List<ParticipantEntry> participants;

    /**
     * 1-5：固定会务（库表 int_meeting_type_preset）；6：其他会议（须填 title）；null：完全自定义（须填 title、company、groupName）
     */
    private Integer presetTypeCode;

    /**
     * 参会人条目。
     */
    @Data
    public static class ParticipantEntry {
        private String userId;
        private String name;
        /** OFFLINE（默认）| ONLINE */
        private String attendanceMode;
    }

    /**
     * Bean Validation：校验预设类型与必填字段组合是否合法。
     *
     * @return 符合预设/自定义规则时为 true
     */
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

package com.smartmeeting.api.dto;

import com.smartmeeting.api.dto.host.HostAgendaItemDto;
import jakarta.validation.constraints.AssertTrue;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 创建会议请求体（{@code POST /api/v1/meetings}）。
 * <p>
 * 支持两种模式：模板会（任意正整数 {@code presetTypeCode}，从库表
 * {@code int_meeting_type_preset} 读取）或完全自定义（title + company + groupName）。
 *
 * @see MeetingResponse
 */
@Data
public class MeetingCreateRequest {
    /** 会议主题；使用模板 {@code presetTypeCode>0} 时可由服务端覆盖 */
    private String title;
    /** 会务议程条目列表（与会务展示用，与 AI 主持议程分离） */
    private List<String> agenda;
    /** AI 主持议题；写入 int_meeting.host_agenda（与会务 agenda 分离） */
    private List<HostAgendaItemDto> hostAgendaItems;
    /** 所属集团；使用模板 {@code presetTypeCode>0} 时由服务端覆盖 */
    private String company;
    private String department;
    /** 会议组；使用模板 {@code presetTypeCode>0} 时由服务端覆盖 */
    private String groupName;
    private String creatorId;
    /** 飞书会话 chat_id，用于推送卡片 */
    private String chatId;
    private String roomId;
    /** 三场景：OFFLINE | HYBRID | ONLINE（不传时根据 participants 自动推导） */
    private String meetingScenario;
    /** 云端录音文件 URL（混合/纯线上可选），用于会后离线处理兜底 */
    private String sourceAudioUrl;
    /** 链路上次会议 ID，用于待办进度通报 */
    private String previousMeetingId;
    private LocalDateTime scheduledTime;
    /** 计划开始时间（ISO-8601）；预约型 create-only API 使用，即时 create-and-start 路径忽略 */
    private List<ParticipantEntry> participants;

    /**
     * 模板编号（正整数）；null 表示完全自定义（须填 title、company、groupName）。
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
    @AssertTrue(message = "请选择有效模板 presetTypeCode（正整数），或填写完整自定义会议（主题、集团、会议组）")
    public boolean isValidPresetOrManual() {
        Integer p = presetTypeCode;
        if (p != null) {
            return p > 0;
        }
        return title != null && !title.trim().isEmpty()
                && company != null && !company.trim().isEmpty()
                && groupName != null && !groupName.trim().isEmpty();
    }
}

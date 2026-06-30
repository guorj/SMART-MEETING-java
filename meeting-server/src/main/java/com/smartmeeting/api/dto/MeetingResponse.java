package com.smartmeeting.api.dto;

import com.smartmeeting.api.dto.host.HostAgendaItemDto;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会议详情响应体。
 * <p>
 * 用于创建、查询、启动/结束会议等接口的 {@code data} 字段，含状态、时间线、参会人与链接信息。
 *
 * @see MeetingCreateRequest
 */
@Data
public class MeetingResponse {
    private String id;
    private String title;
    private List<String> agenda;
    private List<HostAgendaItemDto> hostAgendaItems;
    private String company;
    private String department;
    private String groupName;
    private Integer presetTypeCode;
    private String status;
    private String creatorId;
    private String chatId;
    private String roomId;
    private String meetingScenario;
    private String sourceAudioUrl;
    private String previousMeetingId;
    private LocalDateTime scheduledTime;
    private LocalDateTime actualStartTime;
    private LocalDateTime actualEndTime;
    private Integer durationSeconds;
    /** 飞书纪要文档 URL */
    private String docUrl;
    /** 库内或飞书是否存在可查阅的纪要 */
    private Boolean hasMinute;
    private String recordingUrl;
    /** 飞书 VC 入会链接（建会时持久化） */
    private String vcMeetingUrl;
    /** 妙记 token（recording_ready 回调写入；Admin 可手动填入触发 File B 转写） */
    private String vcMinuteToken;
    /** 妙记页面 URL（回调 event.url，便于人工核对） */
    private String vcRecordingUrl;
    private List<ParticipantDTO> participants;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 参会人摘要，含签到状态与个人入会链接。
     */
    @Data
    public static class ParticipantDTO {
        private String userId;
        private String name;
        private String status;
        private String attendanceMode;
        private LocalDateTime checkedInAt;
        private String checkInSource;
        /** 线上参会人个人入会链接（仅 ONLINE 且已生成时返回） */
        private String joinUrl;
    }
}

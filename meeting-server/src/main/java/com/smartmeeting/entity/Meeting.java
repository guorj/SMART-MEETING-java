package com.smartmeeting.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("int_meeting")
public class Meeting {
    @TableId
    private String id;
    private String title;
    private String agenda;
    private String company;
    private String department;
    private String groupName;
    /** 1-5 固定会务预设，6 其他，NULL 未使用预设 */
    private Integer presetTypeCode;
    private String status;
    private String creatorId;
    private String chatId;    // 飞书群聊ID（用于消息推送）
    private String roomId;
    private String previousMeetingId;
    private LocalDateTime scheduledTime;
    private LocalDateTime actualStartTime;
    private LocalDateTime actualEndTime;
    private Integer durationSeconds;
    private String audioPath;
    private String docUrl;
    private String docToken;
    private String recordingUrl;
    private String recordingToken;  // JWT token, can be 200+ chars
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

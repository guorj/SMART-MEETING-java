package com.smartmeeting.admin.entity;

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
    private String hostAgenda;
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
    private String audioPath;
    private String docUrl;
    private String docToken;
    private String recordingUrl;
    private String recordingToken;
    private String vcMeetingUrl;
    private String vcMinuteToken;
    private String vcRecordingUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

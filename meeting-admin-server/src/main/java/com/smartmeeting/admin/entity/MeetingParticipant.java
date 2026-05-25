package com.smartmeeting.admin.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("int_meeting_participant")
public class MeetingParticipant {
    @TableId
    private String id;
    private String meetingId;
    private String userId;
    private String name;
    private String status;
    private String attendanceMode;
    private LocalDateTime checkedInAt;
    private String checkInSource;
}

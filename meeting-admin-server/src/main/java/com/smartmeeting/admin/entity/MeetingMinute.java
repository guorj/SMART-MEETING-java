package com.smartmeeting.admin.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("int_meeting_minute")
public class MeetingMinute {
    @TableId
    private String meetingId;
    private String contentMarkdown;
    private String contentUrl;
    private Integer contentLength;
    private String generationStatus;
    private LocalDateTime generatedAt;
    private LocalDateTime updatedAt;
}

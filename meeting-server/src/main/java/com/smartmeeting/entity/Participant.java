package com.smartmeeting.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("int_meeting_participant")
public class Participant {
    @TableId
    private String id;
    private String meetingId;
    private String userId;
    private String name;
    private String status;
    private String featureId;
    private Boolean voiceprintReady;
    private Integer todoCount;
    private Integer completedCount;
}

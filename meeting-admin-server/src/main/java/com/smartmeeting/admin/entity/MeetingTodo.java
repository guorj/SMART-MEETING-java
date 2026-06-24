package com.smartmeeting.admin.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("int_meeting_todo")
public class MeetingTodo {
    @TableId
    private String id;
    private String meetingId;
    private Integer presetTypeCode;
    private String content;
    private String assigneeId;
    private String assigneeName;
    private String operatorId;
    private String operatorName;
    private String status;
    private String priority;
    private LocalDateTime deadline;
    private LocalDateTime completedAt;
    private String completionNote;
    private String blockReason;
    private LocalDateTime lastRemindAt;
    private Integer remindCount;
    private String nextMeetingId;
    private Boolean reportedInNext;
    /** 父待办 ID（拆分来源）；NULL 表示原始待办 */
    private String parentId;
    private LocalDateTime createdAt;
}

package com.smartmeeting.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会议待办实体，对应数据库表 {@code int_meeting_todo}。
 * <p>
 * 记录会议中产生的行动项、责任人、优先级、完成状态及跨会议续报信息。
 */
@Data
@TableName("int_meeting_todo")
public class MeetingTodo {
    /** 待办唯一标识（主键） */
    @TableId
    private String id;
    /** 所属会议 ID，外键关联 {@code int_meeting.id} */
    private String meetingId;
    /** 会务预设类型：1-5 模板会，6 自定义，冗余自主表 */
    private Integer presetTypeCode;
    /** 待办内容描述 */
    private String content;
    /** 责任人用户 ID */
    private String assigneeId;
    /** 责任人姓名（展示用） */
    private String assigneeName;
    /** 待办状态，对应 {@link com.smartmeeting.enums.TodoStatus} 枚举名 */
    private String status;
    /** 优先级，对应 {@link com.smartmeeting.enums.Priority} 枚举名 */
    private String priority;
    /** 截止日期 */
    private LocalDateTime deadline;
    /** 实际完成时间 */
    private LocalDateTime completedAt;
    /** 完成说明/备注 */
    private String completionNote;
    /** 阻塞原因（status=BLOCKED 时填写） */
    private String blockReason;
    /** 最后一次催办提醒时间 */
    private LocalDateTime lastRemindAt;
    /** 累计催办次数 */
    private Integer remindCount;
    /** 续报目标的下一次会议 ID */
    private String nextMeetingId;
    /** 是否已在下一次会议中通报 */
    private Boolean reportedInNext;
    /** 记录创建时间 */
    private LocalDateTime createdAt;
}

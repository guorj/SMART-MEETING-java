package com.smartmeeting.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 会议参会人实体，对应数据库表 {@code int_meeting_participant}。
 * <p>
 * 记录参会人身份、到场方式、检点状态及待办统计等信息。
 */
@Data
@TableName("int_meeting_participant")
public class Participant {
    /** 参会记录唯一标识（主键） */
    @TableId
    private String id;
    /** 所属会议 ID，外键关联 {@code int_meeting.id} */
    private String meetingId;
    /** 会务预设类型：正整数模板编码，冗余自主表 */
    private Integer presetTypeCode;
    /** 参会人用户 ID（OA 或飞书映射后的标识） */
    private String userId;
    /** 参会人姓名（展示用） */
    private String name;
    /** 参会确认状态，对应 {@link com.smartmeeting.enums.ConfirmStatus} 枚举名 */
    private String status;
    /** 到场方式：{@code OFFLINE} 线下到场 | {@code ONLINE} 远程接入 */
    private String attendanceMode;
    /** 检点（到场确认）完成时间 */
    private java.time.LocalDateTime checkedInAt;
    /** 检点来源，对应 {@link com.smartmeeting.enums.CheckInSource} 枚举名 */
    private String checkInSource;
    /** 讯飞声纹特征 ID，用于 ASR 说话人识别 */
    private String featureId;
    /** 声纹是否已注册就绪 */
    private Boolean voiceprintReady;
    /** 分配给该参会人的待办总数 */
    private Integer todoCount;
    /** 该参会人已完成待办数 */
    private Integer completedCount;
}

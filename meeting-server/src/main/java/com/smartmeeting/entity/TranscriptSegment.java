package com.smartmeeting.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 实时转写片段实体，对应数据库表 {@code int_transcript_segment}。
 * <p>
 * 存储会议 ASR 转写的逐句文本、说话人、时间戳及置信度等信息。
 */
@Data
@TableName("int_transcript_segment")
public class TranscriptSegment {
    /** 转写片段唯一标识（主键） */
    @TableId
    private String id;
    /** 所属会议 ID，外键关联 {@code int_meeting.id} */
    private String meetingId;
    /** 会务预设类型：1-5 模板会，6 自定义，冗余自主表 */
    private Integer presetTypeCode;
    /** 说话人用户 ID（声纹匹配或人工标注） */
    private String speakerId;
    /** 说话人姓名（展示用） */
    private String speakerName;
    /** 片段起始时间（毫秒，相对会议开始） */
    private Integer startTimeMs;
    /** 片段结束时间（毫秒，相对会议开始） */
    private Integer endTimeMs;
    /** 转写文本内容 */
    private String text;
    /** 是否为 ASR 最终结果（false 表示中间临时结果） */
    private Boolean isFinal;
    /** ASR 识别置信度（0.0-1.0） */
    private Double confidence;
    /** 是否经人工校正 */
    private Boolean corrected;
}

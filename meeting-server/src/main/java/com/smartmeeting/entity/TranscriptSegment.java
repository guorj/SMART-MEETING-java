package com.smartmeeting.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("int_transcript_segment")
public class TranscriptSegment {
    @TableId
    private String id;
    private String meetingId;
    private String speakerId;
    private String speakerName;
    private Integer startTimeMs;
    private Integer endTimeMs;
    private String text;
    private Boolean isFinal;
    private Double confidence;
    private Boolean corrected;
}

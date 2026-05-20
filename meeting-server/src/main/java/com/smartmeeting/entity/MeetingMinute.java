package com.smartmeeting.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会议纪要实体，对应数据库表 {@code int_meeting_minute}。
 * <p>
 * 以会议 ID 为主键，持久化 AI 生成的纪要 Markdown 正文及生成状态。
 */
@Data
@TableName("int_meeting_minute")
public class MeetingMinute {
    /** 所属会议 ID（主键），外键关联 {@code int_meeting.id} */
    @TableId
    private String meetingId;
    /** 纪要正文（Markdown 格式） */
    private String contentMarkdown;
    /** 纪要正文字符数，便于分页与统计 */
    private Integer contentLength;
    /** 生成状态，对应 {@link com.smartmeeting.enums.MinuteGenerationStatus} 枚举名 */
    private String generationStatus;
    /** 纪要首次生成完成时间 */
    private LocalDateTime generatedAt;
    /** 纪要最后更新时间 */
    private LocalDateTime updatedAt;
}

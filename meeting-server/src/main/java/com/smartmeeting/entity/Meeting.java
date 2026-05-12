package com.smartmeeting.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smartmeeting.mybatis.handler.MysqlJsonAsStringTypeHandler;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.time.LocalDateTime;

@Data
@TableName("int_meeting")
public class Meeting {
    @TableId
    private String id;
    private String title;
    @TableField(value = "agenda", jdbcType = JdbcType.OTHER, typeHandler = MysqlJsonAsStringTypeHandler.class)
    private String agenda;
    /** AI 主持议题 JSON：{"items":[{"title","minutes"}]}，与会务 agenda（字符串数组）分离 */
    @TableField(value = "host_agenda", jdbcType = JdbcType.OTHER, typeHandler = MysqlJsonAsStringTypeHandler.class)
    private String hostAgenda;
    private String company;
    private String department;
    private String groupName;
    /** 1-5 固定会务预设，6 其他，NULL 未使用预设 */
    private Integer presetTypeCode;
    private String status;
    private String creatorId;
    private String chatId;    // 飞书群聊ID（用于消息推送）
    private String roomId;
    private String previousMeetingId;
    private LocalDateTime scheduledTime;
    private LocalDateTime actualStartTime;
    private LocalDateTime actualEndTime;
    private Integer durationSeconds;
    private String audioPath;
    private String docUrl;
    private String docToken;
    private String recordingUrl;
    private String recordingToken;  // JWT token, can be 200+ chars
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

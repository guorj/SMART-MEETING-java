package com.smartmeeting.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smartmeeting.mybatis.handler.MysqlJsonAsStringTypeHandler;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.time.LocalDateTime;

/**
 * 会议主表实体，对应数据库表 {@code int_meeting}。
 * <p>
 * 存储会议基本信息、会务预设、飞书群聊关联、录音/文档链接及生命周期时间戳。
 */
@Data
@TableName("int_meeting")
public class Meeting {
    /** 会议唯一标识（主键） */
    @TableId
    private String id;
    /** 会议标题 */
    private String title;
    /** 会务议程 JSON 字符串数组，与会务展示用 */
    @TableField(value = "agenda", jdbcType = JdbcType.OTHER, typeHandler = MysqlJsonAsStringTypeHandler.class)
    private String agenda;
    /** AI 主持议题 JSON：{@code {"items":[{"title","minutes"}]}}，与会务 agenda（字符串数组）分离 */
    @TableField(value = "host_agenda", jdbcType = JdbcType.OTHER, typeHandler = MysqlJsonAsStringTypeHandler.class)
    private String hostAgenda;
    /** 所属公司 */
    private String company;
    /** 所属部门 */
    private String department;
    /** 所属群组/班组名称 */
    private String groupName;
    /** 会务类型预设编码：1-5 为固定预设，6 表示「其他」，NULL 表示未使用预设 */
    private Integer presetTypeCode;
    /** 会议状态，对应 {@link com.smartmeeting.enums.MeetingStatus} 枚举名 */
    private String status;
    /** 创建人用户 ID（OA 或飞书映射后的标识） */
    private String creatorId;
    /** 飞书群聊 ID，用于消息推送与机器人交互 */
    private String chatId;
    /** 飞书会议室 ID（可选） */
    private String roomId;
    /** 关联的上一次会议 ID，用于待办续报等场景 */
    private String previousMeetingId;
    /** 计划开始时间 */
    private LocalDateTime scheduledTime;
    /** 实际开始时间 */
    private LocalDateTime actualStartTime;
    /** 实际结束时间 */
    private LocalDateTime actualEndTime;
    /** 会议实际时长（秒） */
    private Integer durationSeconds;
    /** 本地录音文件存储路径 */
    private String audioPath;
    /** 飞书文档访问 URL */
    private String docUrl;
    /** 飞书文档 token（docx/wiki 等） */
    private String docToken;
    /** 录音页访问 URL（含 JWT） */
    private String recordingUrl;
    /** 录音/主持页 JWT token，长度可达 200+ 字符 */
    private String recordingToken;
    /** 记录创建时间 */
    private LocalDateTime createdAt;
    /** 记录最后更新时间 */
    private LocalDateTime updatedAt;
}

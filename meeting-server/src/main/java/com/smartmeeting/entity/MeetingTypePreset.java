package com.smartmeeting.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smartmeeting.mybatis.handler.MysqlJsonAsStringTypeHandler;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

/**
 * 固定会务类型预设实体，对应数据库表 {@code int_meeting_type_preset}。
 * <p>
 * 存储吉青汽车科技集团等固定会务类型（1-5）的模板信息；编码 6「其他」不入库。
 */
@Data
@TableName("int_meeting_type_preset")
public class MeetingTypePreset {
    /** 预设类型编码（主键），取值 1-5 */
    @TableId
    private Integer code;
    /** 预设展示名称（如「综合例会」） */
    private String displayName;
    /** 默认所属公司 */
    private String company;
    /** 默认所属部门 */
    private String department;
    /** 默认所属群组/班组名称 */
    private String groupName;
    /** 默认排期说明（如「每周一 9:00」） */
    private String scheduleNote;
    /** 默认议程摘要（纯文本） */
    private String agendaSummary;
    /** 默认组织者姓名 */
    private String organizerName;
    /** 默认主持人/领导姓名 */
    private String leaderName;
    /** 中文顿号、逗号或「及」连接的默认与会人名单 */
    private String participantsNames;
    /** AI 主持议题模板 JSON：{@code {"items":[{"title","minutes"},...]}}，业务会议 preset_type_code=1～5 时仅按 code 读本表 */
    @TableField(value = "host_agenda", jdbcType = JdbcType.OTHER, typeHandler = MysqlJsonAsStringTypeHandler.class)
    private String hostAgenda;
}

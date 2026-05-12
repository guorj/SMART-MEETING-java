package com.smartmeeting.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smartmeeting.mybatis.handler.MysqlJsonAsStringTypeHandler;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

/**
 * 吉青汽车科技集团等固定会务类型（1-5），存库可维护；6「其他」不入库。
 */
@Data
@TableName("int_meeting_type_preset")
public class MeetingTypePreset {
    @TableId
    private Integer code;
    private String displayName;
    private String company;
    private String department;
    private String groupName;
    private String scheduleNote;
    private String agendaSummary;
    private String organizerName;
    private String leaderName;
    /** 中文顿号、逗号或「及」连接的与会人名单 */
    private String participantsNames;
    /** AI 主持议题模板 JSON：{"items":[{"title","minutes"},...]}，与 code 对应；业务会议 preset_type_code=1～5 时仅按 code 读本表 */
    @TableField(value = "host_agenda", jdbcType = JdbcType.OTHER, typeHandler = MysqlJsonAsStringTypeHandler.class)
    private String hostAgenda;
}

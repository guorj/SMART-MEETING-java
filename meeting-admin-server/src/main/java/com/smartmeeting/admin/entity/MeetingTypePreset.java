package com.smartmeeting.admin.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smartmeeting.admin.mybatis.handler.MysqlJsonAsStringTypeHandler;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

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
    @TableField(value = "schedule_config", jdbcType = JdbcType.OTHER, typeHandler = MysqlJsonAsStringTypeHandler.class, updateStrategy = FieldStrategy.IGNORED)
    private String scheduleConfig;
    private String agendaSummary;
    private String organizerName;
    private String leaderName;
    private String participantsNames;
    @TableField(value = "host_agenda", jdbcType = JdbcType.OTHER, typeHandler = MysqlJsonAsStringTypeHandler.class)
    private String hostAgenda;
}

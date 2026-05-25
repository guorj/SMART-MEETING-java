package com.smartmeeting.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smartmeeting.admin.mybatis.handler.MysqlJsonAsStringTypeHandler;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.time.LocalDateTime;

@Data
@TableName("int_meeting_system_config_audit")
public class MeetingSystemConfigAudit {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String configKey;
    private String action;
    @TableField(value = "old_value_json", jdbcType = JdbcType.OTHER, typeHandler = MysqlJsonAsStringTypeHandler.class)
    private String oldValueJson;
    @TableField(value = "new_value_json", jdbcType = JdbcType.OTHER, typeHandler = MysqlJsonAsStringTypeHandler.class)
    private String newValueJson;
    private String operator;
    private LocalDateTime createdAt;
}

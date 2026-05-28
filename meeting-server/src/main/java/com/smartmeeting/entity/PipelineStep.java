package com.smartmeeting.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smartmeeting.mybatis.handler.MysqlJsonAsStringTypeHandler;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.time.LocalDateTime;

@Data
@TableName("int_pipeline_step")
public class PipelineStep {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long templateId;
    private String stepCode;
    private String stepName;
    private String stepType;
    private String stage;
    private Integer orderNo;
    private Integer timeoutSeconds;
    @TableField(value = "config_json", jdbcType = JdbcType.OTHER, typeHandler = MysqlJsonAsStringTypeHandler.class)
    private String configJson;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

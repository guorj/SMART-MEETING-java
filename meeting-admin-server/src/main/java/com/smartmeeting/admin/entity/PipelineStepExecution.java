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
@TableName("int_pipeline_step_execution")
public class PipelineStepExecution {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String meetingId;
    private Long templateId;
    private Long stepId;
    private String stage;
    private String status;
    private Integer retryCount;
    private Integer maxRetries;
    private LocalDateTime timeoutAt;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private String lastError;
    @TableField(value = "context_json", jdbcType = JdbcType.OTHER, typeHandler = MysqlJsonAsStringTypeHandler.class)
    private String contextJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

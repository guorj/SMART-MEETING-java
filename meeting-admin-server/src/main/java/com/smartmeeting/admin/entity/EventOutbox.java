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
@TableName("int_event_outbox")
public class EventOutbox {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String aggregateType;
    private String aggregateId;
    private String eventType;
    private String eventKey;
    @TableField(value = "payload_json", jdbcType = JdbcType.OTHER, typeHandler = MysqlJsonAsStringTypeHandler.class)
    private String payloadJson;
    private String status;
    private Integer retryCount;
    private LocalDateTime nextRetryAt;
    private String errorMessage;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

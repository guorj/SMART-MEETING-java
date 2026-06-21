package com.smartmeeting.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("int_processed_command")
public class ProcessedCommand {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String commandKey;
    private String commandType;
    private String aggregateType;
    private String aggregateId;
    private LocalDateTime processedAt;
}

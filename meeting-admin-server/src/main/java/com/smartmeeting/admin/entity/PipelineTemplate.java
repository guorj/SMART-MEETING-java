package com.smartmeeting.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("int_pipeline_template")
public class PipelineTemplate {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String templateCode;
    private String templateName;
    private String stage;
    private Integer enabled;
    private Integer versionNo;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

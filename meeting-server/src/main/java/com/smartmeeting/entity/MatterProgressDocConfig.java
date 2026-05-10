package com.smartmeeting.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("int_matter_progress_doc_config")
public class MatterProgressDocConfig {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String configName;
    private String feishuDocUrl;
    private String feishuDocToken;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

package com.smartmeeting.admin.entity;

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
    private Integer presetTypeCode;
    private Integer agendaIndex;
    private Integer resourceSlot;
    private String feishuDocUrl;
    private Integer enabled;
    private String configRole;
    private String bitableDisplayMode;
    private String generatedReportUrl;
    private LocalDateTime generatedReportAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

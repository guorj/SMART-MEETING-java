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
    /** 会务类型预设 1-5；null 表示全局 legacy 配置 */
    private Integer presetTypeCode;
    /** 对应 host_agenda.items 下标（0-based） */
    private Integer agendaIndex;
    /** 同一会序下多份资料槽位（0-based），与 agenda_index 组成唯一键 */
    private Integer resourceSlot;
    /** 飞书完整链接：/docx/、/wiki/、/base/?table= */
    private String feishuDocUrl;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

package com.smartmeeting.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 事项进度通报飞书资料配置实体，对应数据库表 {@code int_matter_progress_doc_config}。
 * <p>
 * 将会务类型预设、议程下标与飞书文档 URL 绑定，供主持页展示事项进度卡片。
 */
@Data
@TableName("int_matter_progress_doc_config")
public class MatterProgressDocConfig {

    /** 配置记录自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 配置名称（运维识别用） */
    private String configName;
    /** 会务类型预设编码 1-5；null 表示全局 legacy 配置 */
    private Integer presetTypeCode;
    /** 对应 host_agenda.items 下标（0-based） */
    private Integer agendaIndex;
    /** 同一会序下多份资料槽位（0-based），与 agenda_index 组成唯一键 */
    private Integer resourceSlot;
    /** 飞书完整链接：/docx/、/wiki/、/base/?table= 等格式 */
    private String feishuDocUrl;
    /** 是否启用：1 启用，0 禁用 */
    private Integer enabled;
    /** 记录创建时间 */
    private LocalDateTime createdAt;
    /** 记录最后更新时间 */
    private LocalDateTime updatedAt;
}

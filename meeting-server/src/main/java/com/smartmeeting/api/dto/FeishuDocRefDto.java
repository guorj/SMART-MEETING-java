package com.smartmeeting.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单条飞书资料引用（仅 URL；类型由路径解析）。
 * <p>
 * 用于主持议程 {@link com.smartmeeting.api.dto.host.HostAgendaItemDto#getFeishuDocs()} 及运行时文档合并。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeishuDocRefDto {
    /** DOCX / WIKI / BASE */
    private String kind;
    private String url;
}

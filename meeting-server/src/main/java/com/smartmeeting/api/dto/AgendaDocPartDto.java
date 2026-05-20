package com.smartmeeting.api.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 会序下单条飞书资料的拉取结果分项。
 * <p>
 * 作为 {@link AgendaDocContentResponse#getParts()} 的元素。
 */
@Data
@Builder
public class AgendaDocPartDto {
    /** 资料类型，如 DOCX / WIKI / BASE */
    private String docKind;
    private String feishuDocUrl;
    private String plainText;
    /** 无法内嵌拉取时的说明（仍可有 feishuDocUrl 外链） */
    private String fetchError;
}

package com.smartmeeting.api.dto;

import lombok.Builder;
import lombok.Data;

/** 会序下单条飞书资料的拉取结果 */
@Data
@Builder
public class AgendaDocPartDto {
    private String docKind;
    private String feishuDocUrl;
    private String plainText;
    /** 无法内嵌拉取时的说明（仍可有 feishuDocUrl 外链） */
    private String fetchError;
}

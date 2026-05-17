package com.smartmeeting.api.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AgendaDocContentResponse {
    private int agendaIndex;
    private String agendaTitle;
    /** 首条资料（兼容旧前端） */
    private String documentId;
    private String feishuDocUrl;
    private String docKind;
    /** 合并正文（各 part 用标题分隔） */
    private String plainText;
    /** 同一会序多条资料分项 */
    private List<AgendaDocPartDto> parts;
}

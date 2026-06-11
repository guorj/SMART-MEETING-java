package com.smartmeeting.api.dto.structured;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 飞书 DOCX block 结构化输出的单个块。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocxBlockDto {
    /** heading1..heading9 / paragraph / bullet / ordered / quote / todo / divider / image / code */
    private String type;
    /** 纯文字内容（runs 拼接结果） */
    private String text;
    /** 富文本片段列表，保留 bold/italic/link 等属性 */
    private List<RichTextRunDto> runs;
    /** 图片 block 的飞书 image_key */
    private String imageKey;
    /** 子 block 嵌套 */
    private List<DocxBlockDto> children;
    /** 有序 block 序号 */
    private int order;
    /** todo block 是否勾选 */
    private Boolean checked;
    /** type=table 时的行列文本 */
    private List<List<String>> tableRows;
}

package com.smartmeeting.api.dto.structured;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DOCX block runs 中的富文本片段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RichTextRunDto {
    private String text;
    private Boolean bold;
    private Boolean italic;
    private Boolean strikethrough;
    private Boolean underline;
    private String link;
    private String fontSize;
    private String fontColor;
}

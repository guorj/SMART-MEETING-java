package com.smartmeeting.api.dto.structured;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Rich text run: text segment with formatting attributes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TextRunDto {
    private String text;
    private Boolean bold;
    private Boolean italic;
    private Boolean strikethrough;
    private Boolean underline;
    private String link;
}

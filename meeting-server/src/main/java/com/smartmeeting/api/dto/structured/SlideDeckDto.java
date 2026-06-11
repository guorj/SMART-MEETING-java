package com.smartmeeting.api.dto.structured;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PPT/PDF 多页幻灯片集。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlideDeckDto {
    private int totalSlides;
    private List<SlideDto> slides;
    private String title;
}

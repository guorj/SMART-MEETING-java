package com.smartmeeting.api.dto.structured;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PPT/PDF 单页/单张幻灯片。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlideDto {
    private int index;
    /** 代理图片 URL */
    private String imageUrl;
    private int width;
    private int height;
    /** 演讲者备注（PPT 可选） */
    private String notes;
}

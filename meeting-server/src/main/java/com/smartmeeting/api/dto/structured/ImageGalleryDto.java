package com.smartmeeting.api.dto.structured;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 本地图片资料 gallery（contentType=image_gallery）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageGalleryDto {
    private List<ImageGalleryItemDto> items;
}

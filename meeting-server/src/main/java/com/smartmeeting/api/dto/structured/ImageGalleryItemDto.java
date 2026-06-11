package com.smartmeeting.api.dto.structured;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageGalleryItemDto {
    private String fileId;
    private String alt;
    private String caption;
}

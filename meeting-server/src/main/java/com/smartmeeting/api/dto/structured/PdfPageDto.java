package com.smartmeeting.api.dto.structured;
import lombok.AllArgsConstructor; import lombok.Builder; import lombok.Data; import lombok.NoArgsConstructor;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PdfPageDto { private int index; private String imageUrl; private int width; private int height; }

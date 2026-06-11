package com.smartmeeting.service.structured;

import com.smartmeeting.api.dto.structured.DocxBlockDto;
import com.smartmeeting.api.dto.structured.ImageRefDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructuredImageCollectorTest {

    @Test
    void collectsNestedImageKeys() {
        var blocks = List.of(
                DocxBlockDto.builder().type("paragraph").text("intro").build(),
                DocxBlockDto.builder()
                        .type("bullet")
                        .text("item")
                        .children(List.of(
                                DocxBlockDto.builder().type("image").imageKey("img_a").build(),
                                DocxBlockDto.builder().type("image").imageKey("img_b").build()))
                        .build(),
                DocxBlockDto.builder().type("image").imageKey("img_a").build());

        List<ImageRefDto> images = StructuredImageCollector.collectFromDocxBlocks(blocks, "meet-1");
        assertEquals(2, images.size());
        assertEquals("img_a", images.get(0).getId());
        assertTrue(images.get(0).getProxyUrl().contains("meet-1"));
        assertTrue(images.get(0).getProxyUrl().contains("img_a"));
        assertEquals("img_b", images.get(1).getId());
    }
}

package com.smartmeeting.service.structured;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.api.dto.structured.DocxBlockDto;
import com.smartmeeting.api.dto.structured.ImageRefDto;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 从 docx_blocks 结构化内容收集飞书图片引用，供 {@code AgendaDocPartDto.images} 预加载。
 */
public final class StructuredImageCollector {

    private StructuredImageCollector() {
    }

    public static List<ImageRefDto> collectFromDocxBlocks(List<DocxBlockDto> blocks, String meetingId) {
        if (blocks == null || blocks.isEmpty() || meetingId == null || meetingId.isBlank()) {
            return List.of();
        }
        List<ImageRefDto> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (DocxBlockDto block : blocks) {
            walk(block, out, meetingId, seen);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    public static List<ImageRefDto> collectFromStructuredContent(Object structured, String contentType, String meetingId) {
        if (structured == null || meetingId == null || meetingId.isBlank()) {
            return List.of();
        }
        if (!"docx_blocks".equals(contentType)) {
            return List.of();
        }
        if (structured instanceof List<?> list) {
            List<DocxBlockDto> blocks = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof DocxBlockDto dto) {
                    blocks.add(dto);
                }
            }
            return collectFromDocxBlocks(blocks, meetingId);
        }
        return List.of();
    }

    public static List<ImageRefDto> collectFromStructuredJson(
            ObjectMapper mapper, Object structured, String contentType, String meetingId) {
        if (structured == null || mapper == null || !"docx_blocks".equals(contentType)) {
            return List.of();
        }
        try {
            List<DocxBlockDto> blocks = mapper.convertValue(
                    structured,
                    mapper.getTypeFactory().constructCollectionType(List.class, DocxBlockDto.class));
            return collectFromDocxBlocks(blocks, meetingId);
        } catch (IllegalArgumentException e) {
            return List.of();
        }
    }

    private static void walk(DocxBlockDto block, List<ImageRefDto> out, String meetingId, Set<String> seen) {
        if (block == null) {
            return;
        }
        if ("image".equals(block.getType())) {
            String key = block.getImageKey();
            if (key != null && !key.isBlank() && seen.add(key)) {
                out.add(ImageRefDto.builder()
                        .id(key)
                        .proxyUrl(proxyUrl(meetingId, key))
                        .alt("文档图片")
                        .build());
            }
        }
        if (block.getChildren() != null) {
            for (DocxBlockDto child : block.getChildren()) {
                walk(child, out, meetingId, seen);
            }
        }
    }

    static String proxyUrl(String meetingId, String imageKey) {
        return "/api/v1/meetings/" + meetingId.trim()
                + "/agenda-materials/proxy-image?imageKey=" + imageKey;
    }
}

package com.smartmeeting.service.structured;

import com.smartmeeting.api.dto.AgendaDocPartDto;
import com.smartmeeting.api.dto.structured.ImageGalleryDto;
import com.smartmeeting.api.dto.structured.ImageGalleryItemDto;
import com.smartmeeting.config.agenda.AgendaMaterialFileSupport;
import com.smartmeeting.config.agenda.HostAgendaDocBinding;
import com.smartmeeting.config.agenda.HostAgendaItem;
import com.smartmeeting.config.agenda.PresetAgendaMergeEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.service.AgendaMaterialStorageService;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 将 host_agenda 本地上传资料并入 {@code AgendaDocContentResponse.parts[]}。 */
@Slf4j
public final class LocalAgendaMaterialPartBuilder {

    private LocalAgendaMaterialPartBuilder() {
    }

    public static List<AgendaDocPartDto> buildParts(
            Meeting meeting,
            int agendaIndex,
            ObjectMapper objectMapper,
            AgendaMaterialStorageService storageService) {
        if (meeting == null || meeting.getHostAgenda() == null || meeting.getHostAgenda().isBlank()) {
            return List.of();
        }
        List<HostAgendaItem> items = PresetAgendaMergeEngine.parseHostAgendaItems(
                objectMapper, meeting.getHostAgenda());
        if (agendaIndex < 0 || agendaIndex >= items.size()) {
            return List.of();
        }
        HostAgendaItem item = items.get(agendaIndex);
        if (item.getDocs() == null || item.getDocs().isEmpty()) {
            return List.of();
        }
        List<HostAgendaDocBinding> locals = item.getDocs().stream()
                .filter(d -> d != null && d.isEnabled() && d.isLocalStorage() && isSourceRole(d))
                .toList();
        if (locals.isEmpty()) {
            return List.of();
        }
        List<AgendaDocPartDto> parts = new ArrayList<>();
        List<ImageGalleryItemDto> galleryItems = new ArrayList<>();
        for (HostAgendaDocBinding binding : locals) {
            if (binding.getFileId() == null || binding.getFileId().isBlank()) {
                continue;
            }
            String mime = binding.getMimeType();
            if (AgendaMaterialFileSupport.isImageMime(mime)
                    || (binding.getOriginalFilename() != null
                    && binding.getOriginalFilename().toLowerCase(Locale.ROOT).matches(".*\\.(jpg|jpeg|png|gif|webp)$"))) {
                galleryItems.add(ImageGalleryItemDto.builder()
                        .fileId(binding.getFileId())
                        .alt(binding.getOriginalFilename())
                        .caption(binding.getConfigName())
                        .build());
                continue;
            }
            try {
                var preview = storageService.structuredPreview(binding.getFileId());
                if (preview.isPresent()) {
                    var sp = preview.get();
                    AgendaDocPartDto.AgendaDocPartDtoBuilder builder = AgendaDocPartDto.builder()
                            .docKind("LOCAL")
                            .fileId(binding.getFileId())
                            .contentType(sp.contentType())
                            .structuredContent(sp.structuredContent());
                    if ("docx_blocks".equals(sp.contentType()) && meeting.getId() != null) {
                        var images = StructuredImageCollector.collectFromStructuredJson(
                                objectMapper, sp.structuredContent(), sp.contentType(), meeting.getId());
                        if (!images.isEmpty()) {
                            builder.images(images);
                        }
                    }
                    parts.add(builder.build());
                    continue;
                }
                var material = storageService.previewMaterial(binding.getFileId());
                if (material.isPresent() && material.get().getHtml() != null && !material.get().getHtml().isBlank()) {
                    parts.add(AgendaDocPartDto.builder()
                            .docKind("LOCAL")
                            .contentType("html")
                            .structuredContent(material.get().getHtml())
                            .plainText(material.get().getPlainText())
                            .build());
                }
            } catch (Exception e) {
                log.warn("local material part failed fileId={}: {}", binding.getFileId(), e.getMessage());
                parts.add(AgendaDocPartDto.builder()
                        .docKind("LOCAL")
                        .fetchError("本地资料预览失败: " + e.getMessage())
                        .build());
            }
        }
        if (!galleryItems.isEmpty()) {
            parts.add(0, AgendaDocPartDto.builder()
                    .docKind("LOCAL")
                    .contentType("image_gallery")
                    .structuredContent(ImageGalleryDto.builder().items(galleryItems).build())
                    .build());
        }
        return parts;
    }

    private static boolean isSourceRole(HostAgendaDocBinding doc) {
        String role = doc.getRole();
        if (role == null || role.isBlank()) {
            return true;
        }
        role = role.trim().toUpperCase(Locale.ROOT);
        return "SOURCE".equals(role) || "BOTH".equals(role);
    }
}

package com.smartmeeting.admin.api.controller;

import com.smartmeeting.admin.api.dto.AgendaMaterialUploadDto;
import com.smartmeeting.admin.api.dto.ApiResponse;
import com.smartmeeting.admin.service.AgendaMaterialStorageService;
import com.smartmeeting.config.agenda.AgendaMaterialDiskStorage;
import com.smartmeeting.admin.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.Optional;

/**
 * 会序资料本地上传（管理端）。
 */
@RestController
@RequestMapping("/api/v1/admin/agenda-config/materials")
@RequiredArgsConstructor
public class AgendaMaterialAdminController {

    private final AgendaMaterialStorageService storageService;

    @PostMapping("/upload")
    public ApiResponse<AgendaMaterialUploadDto> upload(@RequestParam("file") MultipartFile file) {
        try {
            AgendaMaterialDiskStorage.StoredMaterial stored = storageService.upload(file);
            return ApiResponse.ok(AgendaMaterialUploadDto.builder()
                    .fileId(stored.fileId())
                    .originalFilename(stored.originalFilename())
                    .mimeType(stored.mimeType())
                    .sizeBytes(stored.sizeBytes())
                    .previewUrl("/api/v1/admin/agenda-config/materials/" + stored.fileId())
                    .build());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, e.getMessage());
        } catch (Exception e) {
            throw new BusinessException(500, "上传失败: " + e.getMessage());
        }
    }

    @GetMapping("/{fileId}")
    public ResponseEntity<Resource> download(@PathVariable String fileId) {
        try {
            Optional<AgendaMaterialDiskStorage.StoredMaterial> meta = storageService.loadMeta(fileId);
            Optional<Path> path = storageService.resolvePath(fileId);
            if (meta.isEmpty() || path.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            AgendaMaterialDiskStorage.StoredMaterial m = meta.get();
            String disposition = storageService.isInlineDisposition(m.mimeType()) ? "inline" : "attachment";
            String filename = m.originalFilename() != null ? m.originalFilename() : "file";
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(m.mimeType()))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            disposition + "; filename=\"" + filename.replace("\"", "") + "\"")
                    .body(new FileSystemResource(path.get()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}

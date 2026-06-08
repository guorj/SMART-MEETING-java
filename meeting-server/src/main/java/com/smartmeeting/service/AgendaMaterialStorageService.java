package com.smartmeeting.service;

import com.smartmeeting.api.dto.AgendaMaterialPreviewDto;
import com.smartmeeting.config.AgendaMaterialProperties;
import com.smartmeeting.config.agenda.AgendaMaterialDiskStorage;
import com.smartmeeting.config.agenda.AgendaMaterialDocxPreview;
import com.smartmeeting.config.agenda.AgendaMaterialFileSupport;
import com.smartmeeting.config.agenda.AgendaMaterialPathResolver;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 会序本地上传资料读取（运行时主持页下载/预览）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgendaMaterialStorageService {

    private final AgendaMaterialProperties properties;
    private final List<AgendaMaterialDiskStorage> readDisks = new ArrayList<>();

    @PostConstruct
    void init() {
        for (Path dir : AgendaMaterialPathResolver.candidateStorageDirs(properties.getStorageDir())) {
            readDisks.add(new AgendaMaterialDiskStorage(
                    dir, properties.getMaxImageBytes(), properties.getMaxDocBytes()));
        }
        log.info("Agenda material read paths: {}", readDisks.stream()
                .map(d -> d.storageDir().toAbsolutePath().toString())
                .distinct()
                .toList());
    }

    public Optional<AgendaMaterialDiskStorage.StoredMaterial> loadMeta(String fileId) throws IOException {
        for (AgendaMaterialDiskStorage disk : readDisks) {
            Optional<AgendaMaterialDiskStorage.StoredMaterial> meta = disk.loadMeta(fileId);
            if (meta.isPresent()) {
                return meta;
            }
        }
        return Optional.empty();
    }

    public Optional<Path> resolvePath(String fileId) throws IOException {
        for (AgendaMaterialDiskStorage disk : readDisks) {
            Optional<Path> path = disk.resolveDataPath(fileId);
            if (path.isPresent()) {
                return path;
            }
        }
        return Optional.empty();
    }

    public Optional<AgendaMaterialPreviewDto> previewMaterial(String fileId) throws IOException {
        Optional<AgendaMaterialDiskStorage.StoredMaterial> meta = loadMeta(fileId);
        if (meta.isEmpty()) {
            return Optional.empty();
        }
        Optional<Path> path = resolvePath(fileId);
        if (path.isEmpty()) {
            return Optional.empty();
        }
        String mime = meta.get().mimeType();
        String pathStr = path.get().toString().toLowerCase();
        if (AgendaMaterialFileSupport.isDocMime(mime) || pathStr.endsWith(".docx")) {
            AgendaMaterialDocxPreview.DocxPreview preview = AgendaMaterialDocxPreview.extractPreview(path.get());
            if (preview.html() == null || preview.html().isBlank()) {
                if (preview.plainText() == null || preview.plainText().isBlank()) {
                    return Optional.empty();
                }
                return Optional.of(AgendaMaterialPreviewDto.builder()
                        .plainText(preview.plainText())
                        .html("")
                        .build());
            }
            return Optional.of(AgendaMaterialPreviewDto.builder()
                    .html(preview.html())
                    .plainText(preview.plainText())
                    .build());
        }
        return Optional.empty();
    }

    /** @deprecated 使用 {@link #previewMaterial(String)} */
    public Optional<String> previewPlainText(String fileId) throws IOException {
        return previewMaterial(fileId).map(d -> {
            if (d.getHtml() != null && !d.getHtml().isBlank()) {
                return d.getPlainText() != null ? d.getPlainText() : "";
            }
            return d.getPlainText();
        });
    }

    public boolean isInlineDisposition(String mimeType) {
        return AgendaMaterialFileSupport.isImageMime(mimeType);
    }
}

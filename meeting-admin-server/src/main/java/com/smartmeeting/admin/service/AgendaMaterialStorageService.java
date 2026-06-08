package com.smartmeeting.admin.service;

import com.smartmeeting.admin.config.AgendaMaterialProperties;
import com.smartmeeting.config.agenda.AgendaMaterialDiskStorage;
import com.smartmeeting.config.agenda.AgendaMaterialFileSupport;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Optional;

/**
 * 会序本地上传资料：保存与读取（管理端）。
 */
@Service
@RequiredArgsConstructor
public class AgendaMaterialStorageService {

    private final AgendaMaterialProperties properties;
    private AgendaMaterialDiskStorage disk;

    @PostConstruct
    void init() {
        disk = new AgendaMaterialDiskStorage(
                Path.of(properties.getStorageDir()),
                properties.getMaxImageBytes(),
                properties.getMaxDocBytes());
    }

    public AgendaMaterialDiskStorage disk() {
        return disk;
    }

    public AgendaMaterialDiskStorage.StoredMaterial upload(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的文件");
        }
        String original = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
        try (InputStream in = file.getInputStream()) {
            return disk.save(in, original, file.getContentType(), file.getSize());
        }
    }

    public Optional<AgendaMaterialDiskStorage.StoredMaterial> loadMeta(String fileId) throws IOException {
        return disk.loadMeta(fileId);
    }

    public Optional<Path> resolvePath(String fileId) throws IOException {
        return disk.resolveDataPath(fileId);
    }

    public boolean isInlineDisposition(String mimeType) {
        return AgendaMaterialFileSupport.isImageMime(mimeType);
    }
}

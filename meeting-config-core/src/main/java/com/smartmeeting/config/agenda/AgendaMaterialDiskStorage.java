package com.smartmeeting.config.agenda;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 会序本地上传资料的磁盘读写（无 Spring 依赖，admin/meeting-server 共用）。
 */
public class AgendaMaterialDiskStorage {

    private final Path storageDir;
    private final long maxImageBytes;
    private final long maxDocBytes;

    public AgendaMaterialDiskStorage(Path storageDir, long maxImageBytes, long maxDocBytes) {
        this.storageDir = storageDir;
        this.maxImageBytes = maxImageBytes;
        this.maxDocBytes = maxDocBytes;
    }

    public Path storageDir() {
        return storageDir;
    }

    public StoredMaterial save(InputStream input, String originalFilename, String mimeType, long sizeBytes)
            throws IOException {
        String normalizedMime = AgendaMaterialFileSupport.validateAndNormalizeMime(mimeType, originalFilename);
        if (normalizedMime == null) {
            throw new IllegalArgumentException(
                    "不支持的文件类型，仅允许 doc/docx、ppt/pptx、pdf、xls/xlsx、csv 与常见图片");
        }
        long max = AgendaMaterialFileSupport.maxBytesForMime(normalizedMime, maxImageBytes, maxDocBytes);
        if (sizeBytes <= 0 || sizeBytes > max) {
            throw new IllegalArgumentException("文件大小超出限制");
        }
        Files.createDirectories(storageDir);
        String fileId = UUID.randomUUID().toString();
        String ext = AgendaMaterialFileSupport.extensionForMime(normalizedMime);
        Path dataPath = storageDir.resolve(fileId + ext);
        Files.copy(input, dataPath, StandardCopyOption.REPLACE_EXISTING);
        StoredMaterial meta = new StoredMaterial(fileId, sanitizeFilename(originalFilename),
                normalizedMime, sizeBytes, Instant.now());
        writeMeta(meta);
        return meta;
    }

    public Optional<StoredMaterial> loadMeta(String fileId) throws IOException {
        if (fileId == null || fileId.isBlank()) {
            return Optional.empty();
        }
        Path metaPath = storageDir.resolve(fileId.trim() + ".meta.json");
        if (!Files.isRegularFile(metaPath)) {
            return Optional.empty();
        }
        String json = Files.readString(metaPath);
        return Optional.of(StoredMaterial.fromJson(json));
    }

    public Optional<Path> resolveDataPath(String fileId) throws IOException {
        Optional<StoredMaterial> meta = loadMeta(fileId);
        if (meta.isEmpty()) {
            return Optional.empty();
        }
        String ext = AgendaMaterialFileSupport.extensionForMime(meta.get().mimeType());
        Path path = storageDir.resolve(fileId.trim() + ext);
        return Files.isRegularFile(path) ? Optional.of(path) : Optional.empty();
    }

    private void writeMeta(StoredMaterial meta) throws IOException {
        Path metaPath = storageDir.resolve(meta.fileId() + ".meta.json");
        Files.writeString(metaPath, meta.toJson());
    }

    private static String sanitizeFilename(String name) {
        if (name == null || name.isBlank()) {
            return "file";
        }
        String base = Path.of(name.trim()).getFileName().toString();
        return base.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    public record StoredMaterial(String fileId, String originalFilename, String mimeType,
                                 long sizeBytes, Instant uploadedAt) {

        String toJson() {
            return "{\"fileId\":\"" + escape(fileId) + "\",\"originalFilename\":\"" + escape(originalFilename)
                    + "\",\"mimeType\":\"" + escape(mimeType) + "\",\"sizeBytes\":" + sizeBytes
                    + ",\"uploadedAt\":\"" + uploadedAt.toString() + "\"}";
        }

        static StoredMaterial fromJson(String json) {
            String fileId = extract(json, "fileId");
            String originalFilename = extract(json, "originalFilename");
            String mimeType = extract(json, "mimeType");
            long sizeBytes = 0;
            String sizeStr = extract(json, "sizeBytes");
            if (!sizeStr.isEmpty()) {
                try {
                    sizeBytes = Long.parseLong(sizeStr);
                } catch (NumberFormatException ignored) {
                }
            }
            String uploadedAtStr = extract(json, "uploadedAt");
            Instant uploadedAt = uploadedAtStr.isEmpty() ? Instant.EPOCH : Instant.parse(uploadedAtStr);
            return new StoredMaterial(fileId, originalFilename, mimeType, sizeBytes, uploadedAt);
        }

        private static String extract(String json, String key) {
            String marker = "\"" + key + "\":";
            int i = json.indexOf(marker);
            if (i < 0) {
                return "";
            }
            int start = i + marker.length();
            if (start < json.length() && json.charAt(start) == '"') {
                int end = json.indexOf('"', start + 1);
                return end > start ? json.substring(start + 1, end) : "";
            }
            int end = json.indexOf(',', start);
            if (end < 0) {
                end = json.indexOf('}', start);
            }
            return end > start ? json.substring(start, end).trim() : "";
        }

        private static String escape(String s) {
            return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }
}

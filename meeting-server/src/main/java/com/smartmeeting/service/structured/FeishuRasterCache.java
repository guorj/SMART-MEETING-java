package com.smartmeeting.service.structured;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 飞书 Wiki 附件/幻灯片导出页图的持久化缓存目录（与本地资料 storage-dir 共用根路径）。
 */
public final class FeishuRasterCache {

    private FeishuRasterCache() {
    }

    public static Path cacheRoot(Path storageDir) {
        return storageDir.resolve(".feishu-raster");
    }

    public static Path dirForKey(Path storageDir, String cacheKey) {
        String safe = sanitizeKey(cacheKey);
        return cacheRoot(storageDir).resolve(safe);
    }

    public static void ensureDir(Path storageDir, String cacheKey) throws IOException {
        AgendaMaterialGeneratedImages.ensureDir(dirForKey(storageDir, cacheKey));
    }

    private static String sanitizeKey(String cacheKey) {
        if (cacheKey == null || cacheKey.isBlank()) {
            return "_";
        }
        return cacheKey.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}

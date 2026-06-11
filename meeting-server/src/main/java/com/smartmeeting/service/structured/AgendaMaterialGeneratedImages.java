package com.smartmeeting.service.structured;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 会序本地上传资料（PPT/PDF）导出页图的目标目录与文件名约定。
 */
public final class AgendaMaterialGeneratedImages {

    private AgendaMaterialGeneratedImages() {
    }

    public static Path dirForDataFile(Path dataFile, String fileId) {
        return dataFile.getParent().resolve(".generated").resolve(fileId);
    }

    public static Path pageImage(Path generatedDir, int pageIndex) {
        return generatedDir.resolve("page_" + pageIndex + ".png");
    }

    public static Path slideImage(Path generatedDir, int slideIndex) {
        return generatedDir.resolve("slide_" + slideIndex + ".png");
    }

    public static Path cacheStamp(Path generatedDir) {
        return generatedDir.resolve(".source_mtime");
    }

    public static void ensureDir(Path generatedDir) throws IOException {
        Files.createDirectories(generatedDir);
    }

    /** 源文件未变更且全部页图已存在时复用缓存，避免重复栅格化。 */
    public static boolean isRasterCacheValid(Path sourceFile, Path generatedDir, int pageCount,
                                             java.util.function.IntFunction<Path> imagePath) {
        if (sourceFile == null || pageCount <= 0 || !Files.isRegularFile(sourceFile)) {
            return false;
        }
        Path stamp = cacheStamp(generatedDir);
        if (!Files.isRegularFile(stamp)) {
            return false;
        }
        try {
            String[] lines = Files.readString(stamp).trim().split("\n", 2);
            long savedMtime = Long.parseLong(lines[0].trim());
            int savedCount = lines.length > 1 ? Integer.parseInt(lines[1].trim()) : pageCount;
            long currentMtime = Files.getLastModifiedTime(sourceFile).toMillis();
            if (savedMtime != currentMtime || savedCount != pageCount) {
                return false;
            }
            for (int i = 0; i < pageCount; i++) {
                if (!Files.isRegularFile(imagePath.apply(i))) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static void writeRasterCacheStamp(Path sourceFile, Path generatedDir, int pageCount) throws IOException {
        ensureDir(generatedDir);
        String payload = Files.getLastModifiedTime(sourceFile).toMillis() + "\n" + pageCount;
        Files.writeString(cacheStamp(generatedDir), payload);
    }
}

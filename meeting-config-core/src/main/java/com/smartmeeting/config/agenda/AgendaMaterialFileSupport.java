package com.smartmeeting.config.agenda;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 会序本地上传资料：MIME/扩展名校验与类型判断（首期 doc/docx + 图片）。
 */
public final class AgendaMaterialFileSupport {

    private static final Set<String> ALLOWED_MIMES = Set.of(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    private static final Map<String, String> MIME_TO_EXT = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/gif", ".gif",
            "image/webp", ".webp",
            "application/msword", ".doc",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", ".docx"
    );

    private static final Map<String, String> EXT_TO_MIME = Map.of(
            ".jpg", "image/jpeg",
            ".jpeg", "image/jpeg",
            ".png", "image/png",
            ".gif", "image/gif",
            ".webp", "image/webp",
            ".doc", "application/msword",
            ".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    private AgendaMaterialFileSupport() {
    }

    public static boolean isAllowedMime(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return false;
        }
        return ALLOWED_MIMES.contains(mimeType.trim().toLowerCase(Locale.ROOT));
    }

    public static boolean isImageMime(String mimeType) {
        return mimeType != null && mimeType.toLowerCase(Locale.ROOT).startsWith("image/");
    }

    public static boolean isDocMime(String mimeType) {
        if (mimeType == null) {
            return false;
        }
        String m = mimeType.toLowerCase(Locale.ROOT);
        return "application/msword".equals(m)
                || "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(m);
    }

    public static String extensionForMime(String mimeType) {
        if (mimeType == null) {
            return "";
        }
        return MIME_TO_EXT.getOrDefault(mimeType.trim().toLowerCase(Locale.ROOT), "");
    }

    public static String mimeFromFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "";
        }
        String lower = filename.trim().toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        if (dot < 0) {
            return "";
        }
        return EXT_TO_MIME.getOrDefault(lower.substring(dot), "");
    }

    public static long maxBytesForMime(String mimeType, long maxImageBytes, long maxDocBytes) {
        return isImageMime(mimeType) ? maxImageBytes : maxDocBytes;
    }

    /**
     * 校验上传文件 MIME 与原始文件名扩展名一致且均在白名单内。
     *
     * @return 规范化 MIME；不合法时返回 null
     */
    public static String validateAndNormalizeMime(String mimeType, String originalFilename) {
        String fromName = mimeFromFilename(originalFilename);
        String normalized = mimeType != null ? mimeType.trim().toLowerCase(Locale.ROOT) : "";
        if (normalized.isEmpty()) {
            normalized = fromName;
        }
        if (!isAllowedMime(normalized)) {
            return null;
        }
        if (!fromName.isEmpty() && !fromName.equals(normalized)) {
            return null;
        }
        return normalized;
    }
}

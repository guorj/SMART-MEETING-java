package com.smartmeeting.config.agenda;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 会序本地上传资料：MIME/扩展名校验与类型判断（doc/docx、ppt/pptx、pdf、xls/xlsx、csv + 图片）。
 */
public final class AgendaMaterialFileSupport {

    private static final Set<String> ALLOWED_MIMES = Set.of(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/pdf",
            "text/csv"
    );

    private static final Map<String, String> MIME_TO_EXT = Map.ofEntries(
Map.entry("image/jpeg", ".jpg"),
Map.entry("image/png", ".png"),
Map.entry("image/gif", ".gif"),
Map.entry("image/webp", ".webp"),
Map.entry("application/msword", ".doc"),
Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document", ".docx"),
Map.entry("application/vnd.ms-powerpoint", ".ppt"),
Map.entry("application/vnd.openxmlformats-officedocument.presentationml.presentation", ".pptx"),
Map.entry("application/vnd.ms-excel", ".xls"),
Map.entry("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", ".xlsx"),
Map.entry("application/pdf", ".pdf"),
Map.entry("text/csv", ".csv")
);

    private static final Map<String, String> EXT_TO_MIME = Map.ofEntries(
Map.entry(".jpg", "image/jpeg"),
Map.entry(".jpeg", "image/jpeg"),
Map.entry(".png", "image/png"),
Map.entry(".gif", "image/gif"),
Map.entry(".webp", "image/webp"),
Map.entry(".doc", "application/msword"),
Map.entry(".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
Map.entry(".ppt", "application/vnd.ms-powerpoint"),
Map.entry(".pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
Map.entry(".xls", "application/vnd.ms-excel"),
Map.entry(".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
Map.entry(".pdf", "application/pdf"),
Map.entry(".csv", "text/csv")
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
                || "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(m)
                || "application/vnd.ms-powerpoint".equals(m)
                || "application/vnd.openxmlformats-officedocument.presentationml.presentation".equals(m)
                || "application/vnd.ms-excel".equals(m)
                || "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet".equals(m)
                || "application/pdf".equals(m)
                || "text/csv".equals(m);
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
        if (isImageMime(mimeType)) return maxImageBytes;
        String m = mimeType != null ? mimeType.toLowerCase(Locale.ROOT) : "";
        if (m.contains("presentation") || m.contains("powerpoint") || m.contains("pdf")) {
            return Math.max(maxDocBytes, 50L * 1024 * 1024);
        }
        if (m.contains("excel") || m.contains("spreadsheet")) {
            return Math.max(maxDocBytes, 20L * 1024 * 1024);
        }
        return maxDocBytes;
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
        if ("application/octet-stream".equals(normalized) && !fromName.isEmpty() && isAllowedMime(fromName)) {
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

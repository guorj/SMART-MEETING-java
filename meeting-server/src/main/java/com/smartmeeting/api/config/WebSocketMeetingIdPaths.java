package com.smartmeeting.api.config;

/**
 * 从 WebSocket 握手 URI 解析会议 ID。
 * <p>
 * 须兼容 {@code server.servlet.context-path}（如 {@code /meeting-server/ws/host/{id}}）
 * 与裸路径 {@code /ws/host/{id}}。
 */
final class WebSocketMeetingIdPaths {

    private WebSocketMeetingIdPaths() {
    }

    /**
     * @param path {@link org.springframework.web.socket.WebSocketSession#getUri()} 的 path 部分
     * @return 会议 ID；无法解析时为 null
     */
    static String meetingIdFromPath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String[] parts = path.split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            if ("host".equals(parts[i]) || "audio".equals(parts[i])) {
                String id = parts[i + 1];
                if (id != null && !id.isBlank()) {
                    return id;
                }
            }
        }
        for (int i = parts.length - 1; i >= 0; i--) {
            if (!parts[i].isBlank()) {
                return parts[i];
            }
        }
        return null;
    }
}

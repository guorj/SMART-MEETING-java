package com.smartmeeting.event;

import java.util.List;

/**
 * 会后离线 ASR 请求领域事件，由 Outbox 转为可重试异步消息。
 */
public record OfflineAsrRequestedEvent(
        String meetingId,
        String audioPath,
        List<String> featureIds,
        String modelName,
        long sentAt
) {
}

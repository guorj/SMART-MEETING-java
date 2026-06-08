package com.smartmeeting.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 离线 ASR 异步消息体（Kafka / {@link com.smartmeeting.mq.LocalEventBus} 共用）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfflineAsrMessage {

    private String meetingId;
    private String audioPath;
    private List<String> featureIds;
    private String modelName;
    private Long sentAt;
}

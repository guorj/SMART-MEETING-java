package com.smartmeeting.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 纪要生成异步消息体（Kafka / {@link com.smartmeeting.mq.LocalEventBus} 共用）。
 *
 * <p>会议结束或手动触发后发布，由 {@link com.smartmeeting.mq.MinuteGenerateConsumer}
 * 或本地事件总线消费，驱动 {@link com.smartmeeting.service.MinuteGenerationService}。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MinuteGenerateMessage {

    /** 会议主键 */
    private String meetingId;

    /** 本地或对象存储中的会议音频路径（PCM/WAV 等） */
    private String audioPath;

    /** 参会人已注册声纹的 featureId 列表，供 ISV 说话人识别 */
    private List<String> featureIds;

    /** LLM 模型名称（可选，消费端可覆盖配置） */
    private String modelName;

    /** 消息发送时刻（epoch 毫秒，可选） */
    private Long sentAt;
}

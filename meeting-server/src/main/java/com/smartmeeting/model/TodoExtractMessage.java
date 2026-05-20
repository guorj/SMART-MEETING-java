package com.smartmeeting.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 待办提取异步消息体（Kafka / {@link com.smartmeeting.mq.LocalEventBus} 共用）。
 *
 * <p>纪要就绪后发布，由 {@link com.smartmeeting.mq.TodoExtractConsumer} 消费，
 * 驱动 {@link com.smartmeeting.service.TodoExtractionService} 从纪要文本拆解待办并匹配责任人。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TodoExtractMessage {

    /** 会议主键 */
    private String meetingId;

    /** 用于提取的纪要全文（Markdown 或纯文本） */
    private String minuteText;

    /** 本场参会人列表，供 LLM 匹配责任人 */
    private List<ParticipantInfo> participants;

    /** 消息发送时刻（epoch 毫秒，可选） */
    private Long sentAt;

    /**
     * 参会人简要信息（待办责任人匹配用）。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParticipantInfo {

        /** 飞书 open_id 或内部用户 ID */
        private String userId;

        /** 显示姓名 */
        private String name;
    }
}

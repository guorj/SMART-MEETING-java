package com.smartmeeting.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * oabp 任务回写消息体（Outbox {@link com.smartmeeting.outbox.OutboxEventTypes#OABP_TASK_WRITEBACK} 载荷）。
 *
 * <p>由主库事务在 {@code AFTER_COMMIT} 阶段写入 {@code int_event_outbox}，
 * 由 {@code com.smartmeeting.service.oabp.outbox.OabpTaskWritebackConsumer} 异步消费，
 * 写入 oabp {@code jq_todos_task}。</p>
 *
 * <p>幂等键约定：{@code eventKey = "oabp-task-writeback:" + meetingTodoId + ":" + sentAt}，
 * 同一待办多次触发由 outbox 唯一索引 {@code uk_event_outbox_key} 保证只消费一次。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OabpTaskWritebackMessage {

    /** meeting 待办 ID（{@code int_meeting_todo.id}） */
    private String meetingTodoId;

    /** 会议 ID */
    private String meetingId;

    /** 任务名称（待办内容） */
    private String taskName;

    /** 业务板块/项目类别；默认"未分类" */
    private String businessBlock;

    /** 进度 0-100；null 时消费者按状态推断 */
    private Integer progress;

    /** 开始日期（可空，消费者回退到计划日期或当天） */
    private LocalDate startDate;

    /** 计划完成日期（截止日期） */
    private LocalDate plannedEndDate;

    /** 状态：0=未开始，1=进行中，2=已完成，3=已延期 */
    private Integer status;

    /** 长期任务详情（可空） */
    private String taskDetail;

    /** 备注；若调用方未指定，消费者写入 {@code [meeting:todoId=xxx]} 便于回溯 */
    private String remark;

    /** 租户 ID（bigint，可空，消费者默认 0） */
    private Long tenantId;

    /** 触发来源标识，如 {@code meeting-ended}、{@code pipeline-post}；用于审计 */
    private String source;

    /** 消息发送时刻（epoch 毫秒） */
    private Long sentAt;
}

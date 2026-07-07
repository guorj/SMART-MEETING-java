package com.smartmeeting.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * oabp 跟进同步消息体（Outbox {@link com.smartmeeting.outbox.OutboxEventTypes#OABP_FOLLOWUP_SYNC} 载荷）。
 *
 * <p>由 {@code com.smartmeeting.service.oabp.outbox.OabpFollowupSyncConsumer} 异步消费，
 * 向 oabp {@code jq_todos_task_followup} 插入一条跟进记录。</p>
 *
 * <p>字段对齐 oabp 库实际 DDL：无 progress/status，进度信息记录在文本字段中；
 * {@code reportDate} 为 NOT NULL 必填，{@code taskType} 默认 0=主任务跟进（DDL 默认 1=子任务，业务约定覆盖）。</p>
 *
 * <p>幂等键约定：{@code eventKey = "oabp-followup-sync:" + meetingTodoId + ":" + sentAt}。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OabpFollowupSyncMessage {

    /** meeting 待办 ID（{@code int_meeting_todo.id}） */
    private String meetingTodoId;

    /** 关联的 oabp 任务 ID（{@code jq_todos_task.id}）；null 时消费者按 remark 查找 */
    private Long oabpTaskId;

    /** 任务类型；0=主任务跟进（业务默认），1=子任务跟进（DDL 默认 1，需显式覆盖） */
    private Integer taskType;

    /** 任务跟进记录（对应 DDL followup_content） */
    private String followupContent;

    /** 上周进度（对应 DDL last_week_progress） */
    private String lastWeekProgress;

    /** 本周计划（对应 DDL this_week_plan） */
    private String thisWeekPlan;

    /** 汇报日期（DDL NOT NULL 必填；消费者在消息未提供时回退到当天） */
    private LocalDate reportDate;

    /** 备注（可空，写入 remark 字段便于回溯；非 DDL 字段，由消费者拼到 followup_content） */
    private String remark;

    /** 租户 ID（bigint，可空，消费者默认 0） */
    private Long tenantId;

    /** 触发来源标识，如 {@code todo-completed}、{@code todo-reminder}、{@code weekly-report}；用于审计 */
    private String source;

    /** 消息发送时刻（epoch 毫秒） */
    private Long sentAt;
}

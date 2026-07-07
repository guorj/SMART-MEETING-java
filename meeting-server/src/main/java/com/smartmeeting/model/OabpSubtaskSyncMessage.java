package com.smartmeeting.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * oabp 子任务（执行人）同步消息体（Outbox {@link com.smartmeeting.outbox.OutboxEventTypes#OABP_SUBTASK_SYNC} 载荷）。
 *
 * <p>由主库事务在 {@code AFTER_COMMIT} 阶段写入 {@code int_event_outbox}，
 * 由 {@code com.smartmeeting.service.oabp.outbox.OabpSubtaskSyncConsumer} 异步消费，
 * 写入 oabp {@code jq_todos_subtask}。</p>
 *
 * <p><b>字段语义</b>：{@code jq_todos_subtask} 表无 status/progress 字段，只承载执行人指派信息；
 * 状态/进度同步走 {@link OabpTaskWritebackMessage}（写 jq_todos_task）。</p>
 *
 * <p><b>幂等策略</b>：消费者按 {@code parent_id + asignee_id} 查找已存在 subtask，
 * 存在则 UPDATE，不存在则 INSERT。同一责任人不会重复建 subtask。</p>
 *
 * <p><b>asignee_id 类型</b>：{@code Integer}，对应 oabp {@code system_users.id}（数字工号）。
 * 调用方需先用 {@code OabpAssigneeResolver} 把飞书 user_id 反查为 OABP 工号再传入。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OabpSubtaskSyncMessage {

    /** meeting 待办 ID（{@code int_meeting_todo.id}） */
    private String meetingTodoId;

    /** oabp 主任务 ID（可空，消费者按 remark 前缀 {@code [meeting:todoId=xxx]} 查找） */
    private Long oabpTaskId;

    /** 执行人 OABP 系统用户 ID（{@code system_users.id}，数字工号）；null 时跳过 subtask 同步 */
    private Integer assigneeOaUserId;

    /** 子任务名称（一般取待办内容） */
    private String subtaskName;

    /** 触发来源标识，如 {@code todo-extraction}、{@code todo-assign}；用于审计 */
    private String source;

    /** 消息发送时刻（epoch 毫秒） */
    private Long sentAt;
}

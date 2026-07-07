package com.smartmeeting.outbox;

/**
 * Outbox 事件类型常量。
 * <p>
 * 新增类型时同步在 {@link OutboxDispatcher} 增加分发分支。
 * </p>
 */
public final class OutboxEventTypes {
    private OutboxEventTypes() {}

    /** 离线 ASR 请求 */
    public static final String OFFLINE_ASR = "OFFLINE_ASR";
    /** 纪要生成 */
    public static final String MINUTE_GENERATE = "MINUTE_GENERATE";
    /** 待办提取 */
    public static final String TODO_EXTRACT = "TODO_EXTRACT";

    /**
     * oabp 任务回写：将 meeting 待办同步到 oabp {@code jq_todos_task}。
     * <p>
     * 跨库一致性场景：主库事务提交后，由 {@link OutboxPoller} 异步消费，写入 oabp 库。
     * 消费者：{@code com.smartmeeting.service.oabp.outbox.OabpTaskWritebackConsumer}
     * </p>
     */
    public static final String OABP_TASK_WRITEBACK = "OABP_TASK_WRITEBACK";

    /**
     * oabp 跟进同步：将 meeting 待办进度同步到 oabp {@code jq_todos_task_followup}。
     * <p>
     * 消费者：{@code com.smartmeeting.service.oabp.outbox.OabpFollowupSyncConsumer}
     * </p>
     */
    public static final String OABP_FOLLOWUP_SYNC = "OABP_FOLLOWUP_SYNC";

    /**
     * oabp 子任务（执行人）同步：将 meeting 待办责任人同步到 oabp {@code jq_todos_subtask}。
     * <p>
     * 消费者：{@code com.smartmeeting.service.oabp.outbox.OabpSubtaskSyncConsumer}
     * </p>
     * <p>
     * 幂等策略：按 {@code parent_id + asignee_id} 查找已存在 subtask，存在则 UPDATE，不存在则 INSERT。
     * </p>
     */
    public static final String OABP_SUBTASK_SYNC = "OABP_SUBTASK_SYNC";
}

package com.smartmeeting.service.oabp.outbox;

/**
 * oabp Outbox 消费者包说明。
 *
 * <p>本包下的消费者由 {@link com.smartmeeting.outbox.OutboxDispatcher} 调用，
 * 在 {@code oabpTransactionManager} 事务内写入 oabp 库，实现 intelligence → oabp 的最终一致性。</p>
 *
 * <p><b>事务边界</b>：消费者方法必须显式 {@code @Transactional("oabpTransactionManager")}，
 * 与主库事务隔离；调用方（{@link com.smartmeeting.outbox.OutboxPoller}）在主库事务内更新 outbox 状态，
 * 两库不在同一事务，失败由 outbox 重试机制兜底（最多 8 次）。</p>
 *
 * <p><b>幂等性</b>：消费者实现需保证幂等——同一 eventKey 重复消费不应产生副作用。
 * 推荐做法：先按业务键查询 oabp 是否已存在，存在则 UPDATE，不存在则 INSERT。</p>
 *
 * <p><b>与流水线的关系</b>：本包消费者可被未来 Pipeline StepExecutor 复用——
 * 流水线步骤的 {@code step_type} 可指向本包消费者类名，由 Pipeline 执行器反射调用。
 * 当前阶段先由 OutboxPoller 驱动，待 Pipeline 执行器实现后无缝接入。</p>
 */
final class OabpOutboxPackageInfo {
    private OabpOutboxPackageInfo() {}
}

package com.smartmeeting.service.oabp.outbox;

import com.smartmeeting.entity.oabp.OabpJqTodosSubtask;
import com.smartmeeting.entity.oabp.OabpJqTodosTask;
import com.smartmeeting.model.OabpSubtaskSyncMessage;
import com.smartmeeting.repository.oabp.OabpJqTodosSubtaskMapper;
import com.smartmeeting.repository.oabp.OabpJqTodosTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * oabp 子任务（执行人）同步消费者：将 meeting 待办责任人同步到 oabp {@code jq_todos_subtask}。
 *
 * <p>由 {@link com.smartmeeting.outbox.OutboxDispatcher} 在 outbox 事件类型为
 * {@link com.smartmeeting.outbox.OutboxEventTypes#OABP_SUBTASK_SYNC} 时调用。</p>
 *
 * <p><b>幂等策略</b>：按 {@code parent_id + asignee_id} 查找已存在 subtask，
 * 存在则 UPDATE {@code task_name} / {@code updater} / {@code update_time}，
 * 不存在则 INSERT。同一责任人不会重复建 subtask。</p>
 *
 * <p><b>事务</b>：方法显式绑定 {@code oabpTransactionManager}，与主库 outbox 状态更新事务隔离；
 * 抛出异常时 oabp 写入回滚，outbox 由 {@link com.smartmeeting.outbox.OutboxPoller} 重试。</p>
 *
 * <p><b>任务定位</b>：优先用消息中的 {@code oabpTaskId}；为空时按
 * {@code remark} 前缀 {@code [meeting:todoId=xxx]} 查找已回写的 {@code jq_todos_task}。
 * 若任务不存在则抛异常触发重试（通常意味着 {@link OabpTaskWritebackConsumer} 尚未执行完成）。</p>
 *
 * <p><b>跳过条件</b>：{@code assigneeOaUserId} 为 null 或 {@code <= 0} 时跳过（责任人无法映射到 OABP 工号），
 * 不抛异常，仅记录日志——避免阻塞 outbox 链路。</p>
 *
 * <p><b>字段类型注意</b>：{@code jq_todos_subtask} 的 {@code id/parent_id/asignee_id} 均为 int，
 * {@code parent_id} 关联 {@code jq_todos_task.id}（bigint）需类型转换。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "meeting.datasource.external.oabp", name = "enabled", havingValue = "true")
public class OabpSubtaskSyncConsumer {

    /** remark 前缀，与 {@link OabpTaskWritebackConsumer} 保持一致 */
    private static final String REMARK_PREFIX_FORMAT = "[meeting:todoId=%s]";

    /** 默认租户 ID（DDL NOT NULL DEFAULT 0） */
    private static final int DEFAULT_TENANT_ID = 0;

    private final OabpJqTodosSubtaskMapper subtaskMapper;
    private final OabpJqTodosTaskMapper taskMapper;

    /**
     * 消费子任务同步消息，幂等写入 oabp {@code jq_todos_subtask}。
     *
     * @param message 子任务同步消息
     * @throws Exception 任务未找到或写入失败时抛出，由 outbox 重试机制兜底
     */
    @Transactional("oabpTransactionManager")
    public void consume(OabpSubtaskSyncMessage message) throws Exception {
        if (message == null || message.getMeetingTodoId() == null) {
            throw new IllegalArgumentException("OabpSubtaskSyncMessage.meetingTodoId 不能为空");
        }
        if (message.getAssigneeOaUserId() == null || message.getAssigneeOaUserId() <= 0) {
            log.info("oabp subtask sync skipped (no asignee OABP id): meetingTodoId={}",
                    message.getMeetingTodoId());
            return;
        }

        Long oabpTaskId = resolveOabpTaskId(message);
        if (oabpTaskId == null) {
            throw new IllegalStateException(
                    "oabp 任务未找到，meetingTodoId=" + message.getMeetingTodoId()
                            + "；请等待 OABP_TASK_WRITEBACK 消费完成后重试");
        }

        OabpJqTodosSubtask existing = subtaskMapper.findByParentIdAndAsigneeId(
                oabpTaskId, message.getAssigneeOaUserId());
        if (existing != null) {
            updateSubtask(existing, message);
            log.info("oabp subtask sync UPDATE: meetingTodoId={}, oabpTaskId={}, subtaskId={}, asigneeId={}",
                    message.getMeetingTodoId(), oabpTaskId, existing.getId(), message.getAssigneeOaUserId());
        } else {
            Integer newId = insertSubtask(message, oabpTaskId);
            log.info("oabp subtask sync INSERT: meetingTodoId={}, oabpTaskId={}, subtaskId={}, asigneeId={}",
                    message.getMeetingTodoId(), oabpTaskId, newId, message.getAssigneeOaUserId());
        }
    }

    /**
     * 解析 oabp 主任务 ID：消息已提供则直接用，否则按 remark 前缀查找已回写的任务。
     *
     * @param message 消息体
     * @return oabp 主任务 ID；查找失败返回 null
     */
    private Long resolveOabpTaskId(OabpSubtaskSyncMessage message) {
        if (message.getOabpTaskId() != null) {
            return message.getOabpTaskId();
        }
        String prefix = String.format(REMARK_PREFIX_FORMAT + "%%", message.getMeetingTodoId());
        OabpJqTodosTask task = taskMapper.findByRemarkPrefix(prefix).stream().findFirst().orElse(null);
        return task == null ? null : task.getId();
    }

    /**
     * 插入新子任务。
     *
     * @param message    消息体
     * @param oabpTaskId 父任务 ID
     * @return 新子任务 ID
     */
    private Integer insertSubtask(OabpSubtaskSyncMessage message, Long oabpTaskId) {
        OabpJqTodosSubtask subtask = new OabpJqTodosSubtask();
        subtask.setParentId(oabpTaskId.intValue());
        subtask.setTaskName(message.getSubtaskName());
        subtask.setAsigneeId(message.getAssigneeOaUserId());
        subtask.setDeleted(0);
        subtask.setTenantId(DEFAULT_TENANT_ID);
        subtask.setCreator(message.getAssigneeOaUserId());
        subtask.setUpdater(message.getAssigneeOaUserId());
        subtaskMapper.insert(subtask);
        return subtask.getId();
    }

    /**
     * 更新已存在子任务的名称与操作人。
     *
     * @param existing 已存在子任务
     * @param message  消息体
     */
    private void updateSubtask(OabpJqTodosSubtask existing, OabpSubtaskSyncMessage message) {
        if (message.getSubtaskName() != null && !message.getSubtaskName().isBlank()) {
            existing.setTaskName(message.getSubtaskName());
        }
        existing.setUpdater(message.getAssigneeOaUserId());
        subtaskMapper.updateById(existing);
    }
}

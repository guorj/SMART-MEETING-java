package com.smartmeeting.service.oabp.outbox;

import com.smartmeeting.entity.oabp.OabpJqTodosTask;
import com.smartmeeting.entity.oabp.OabpJqTodosTaskFollowup;
import com.smartmeeting.model.OabpFollowupSyncMessage;
import com.smartmeeting.repository.oabp.OabpJqTodosTaskFollowupMapper;
import com.smartmeeting.repository.oabp.OabpJqTodosTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * oabp 跟进同步消费者：向 oabp {@code jq_todos_task_followup} 插入跟进记录。
 *
 * <p>由 {@link com.smartmeeting.outbox.OutboxDispatcher} 在 outbox 事件类型为
 * {@link com.smartmeeting.outbox.OutboxEventTypes#OABP_FOLLOWUP_SYNC} 时调用。</p>
 *
 * <p><b>幂等策略</b>：每次调用均插入一条新跟进记录（followup 表天然追加语义），
 * 幂等性由 outbox 的 {@code event_key} 唯一索引保证——同一 eventKey 不会被重复消费。</p>
 *
 * <p><b>事务</b>：方法显式绑定 {@code oabpTransactionManager}；抛出异常时 oabp 写入回滚，
 * outbox 由 {@link com.smartmeeting.outbox.OutboxPoller} 重试。</p>
 *
 * <p><b>任务定位</b>：优先用消息中的 {@code oabpTaskId}；为空时按
 * {@code remark} 前缀 {@code [meeting:todoId=xxx]} 查找已回写的任务。若任务不存在则抛异常触发重试
 * （通常意味着 {@link OabpTaskWritebackConsumer} 尚未执行完成，重试后即可成功）。</p>
 *
 * <p><b>必填字段</b>：{@code report_date} 为 DDL NOT NULL，消息未提供时消费者回退到当天；
 * {@code task_type} 业务约定默认 0=主任务跟进（DDL 默认 1=子任务，需显式覆盖）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "meeting.datasource.external.oabp", name = "enabled", havingValue = "true")
public class OabpFollowupSyncConsumer {

    /** remark 前缀，与 {@link OabpTaskWritebackConsumer} 保持一致 */
    private static final String REMARK_PREFIX_FORMAT = "[meeting:todoId=%s]";

    /** 默认任务类型：主任务跟进（覆盖 DDL 默认 1=子任务） */
    private static final int DEFAULT_TASK_TYPE = 0;

    /** 默认租户 ID（DDL NOT NULL DEFAULT 0） */
    private static final long DEFAULT_TENANT_ID = 0L;

    private final OabpJqTodosTaskFollowupMapper followupMapper;
    private final OabpJqTodosTaskMapper taskMapper;

    /**
     * 消费跟进同步消息，向 oabp {@code jq_todos_task_followup} 插入一条记录。
     *
     * @param message 跟进同步消息
     * @throws Exception 写入失败或关联任务未找到时抛出，由 outbox 重试机制兜底
     */
    @Transactional("oabpTransactionManager")
    public void consume(OabpFollowupSyncMessage message) throws Exception {
        if (message == null || message.getMeetingTodoId() == null) {
            throw new IllegalArgumentException("OabpFollowupSyncMessage.meetingTodoId 不能为空");
        }

        Long oabpTaskId = resolveOabpTaskId(message);
        OabpJqTodosTaskFollowup followup = buildFollowup(message, oabpTaskId);
        followupMapper.insert(followup);
        log.info("oabp followup sync INSERT: meetingTodoId={}, oabpTaskId={}, followupId={}",
                message.getMeetingTodoId(), oabpTaskId, followup.getId());
    }

    /**
     * 解析 oabp 任务 ID：消息已提供则直接用，否则按 remark 前缀查找已回写的任务。
     *
     * @param message 消息体
     * @return oabp 任务 ID
     * @throws IllegalStateException 关联任务未找到时
     */
    private Long resolveOabpTaskId(OabpFollowupSyncMessage message) {
        if (message.getOabpTaskId() != null) {
            return message.getOabpTaskId();
        }
        String prefix = String.format(REMARK_PREFIX_FORMAT + "%%", message.getMeetingTodoId());
        OabpJqTodosTask task = taskMapper.findByRemarkPrefix(prefix).stream().findFirst().orElse(null);
        if (task == null) {
            throw new IllegalStateException(
                    "oabp 任务未找到，meetingTodoId=" + message.getMeetingTodoId()
                            + "；请等待 OABP_TASK_WRITEBACK 消费完成后重试");
        }
        return task.getId();
    }

    /**
     * 构建跟进记录实体。
     *
     * @param message   消息体
     * @param oabpTaskId oabp 任务 ID
     * @return 待插入的跟进记录
     */
    private OabpJqTodosTaskFollowup buildFollowup(OabpFollowupSyncMessage message, Long oabpTaskId) {
        OabpJqTodosTaskFollowup followup = new OabpJqTodosTaskFollowup();
        followup.setTaskId(oabpTaskId);
        followup.setTaskType(message.getTaskType() != null ? message.getTaskType() : DEFAULT_TASK_TYPE);
        followup.setFollowupContent(resolveFollowupContent(message));
        followup.setLastWeekProgress(message.getLastWeekProgress());
        followup.setThisWeekPlan(message.getThisWeekPlan());
        // report_date 为 DDL NOT NULL，消息未提供时回退到当天
        followup.setReportDate(message.getReportDate() != null ? message.getReportDate() : LocalDate.now());
        followup.setDeleted(false);
        followup.setTenantId(message.getTenantId() != null ? message.getTenantId() : DEFAULT_TENANT_ID);
        followup.setCreator(resolveOperator(message.getSource()));
        followup.setUpdater(resolveOperator(message.getSource()));
        return followup;
    }

    /**
     * 解析跟进内容：消息已提供 followupContent 则直接用，否则将 remark 拼入内容便于回溯。
     *
     * @param message 消息体
     * @return 跟进内容
     */
    private String resolveFollowupContent(OabpFollowupSyncMessage message) {
        if (message.getFollowupContent() != null && !message.getFollowupContent().isBlank()) {
            return message.getFollowupContent();
        }
        if (message.getRemark() != null && !message.getRemark().isBlank()) {
            return message.getRemark();
        }
        return null;
    }

    /**
     * 解析操作人标识。
     *
     * @param source 触发来源
     * @return 操作人字符串
     */
    private String resolveOperator(String source) {
        return source != null && !source.isBlank() ? "meeting-outbox:" + source : "meeting-outbox";
    }
}

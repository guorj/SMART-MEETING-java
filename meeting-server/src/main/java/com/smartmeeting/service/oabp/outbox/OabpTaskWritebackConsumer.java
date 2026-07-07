package com.smartmeeting.service.oabp.outbox;

import com.smartmeeting.entity.oabp.OabpJqTodosTask;
import com.smartmeeting.model.OabpTaskWritebackMessage;
import com.smartmeeting.repository.oabp.OabpJqTodosTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * oabp 任务回写消费者：将 meeting 待办同步到 oabp {@code jq_todos_task}。
 *
 * <p>由 {@link com.smartmeeting.outbox.OutboxDispatcher} 在 outbox 事件类型为
 * {@link com.smartmeeting.outbox.OutboxEventTypes#OABP_TASK_WRITEBACK} 时调用。</p>
 *
 * <p><b>幂等策略</b>：按 {@code remark} 前缀 {@code [meeting:todoId=xxx]} 查找 oabp 任务，
 * 存在则 UPDATE，不存在则 INSERT。同一 {@code meetingTodoId} 重复触发不会产生重复行。</p>
 *
 * <p><b>事务</b>：方法显式绑定 {@code oabpTransactionManager}，与主库 outbox 状态更新事务隔离；
 * 抛出异常时 oabp 写入回滚，outbox 由 {@link com.smartmeeting.outbox.OutboxPoller} 重试。</p>
 *
 * <p><b>状态映射</b>：meeting 待办状态（{@link com.smartmeeting.enums.TodoStatus}）由调用方在
 * 构造 {@link OabpTaskWritebackMessage} 时转成 oabp 数字状态（0/1/2/3）；本消费者不做状态枚举翻译，
 * 直接透传 {@link OabpTaskWritebackMessage#getStatus()}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "meeting.datasource.external.oabp", name = "enabled", havingValue = "true")
public class OabpTaskWritebackConsumer {

    /** remark 前缀，用于幂等查找；格式 {@code [meeting:todoId=xxx]} */
    private static final String REMARK_PREFIX_FORMAT = "[meeting:todoId=%s]";

    /** 默认业务板块，调用方未指定时使用 */
    private static final String DEFAULT_BUSINESS_BLOCK = "未分类";

    /** 默认状态：未开始 */
    private static final int DEFAULT_STATUS = 0;

    /** 默认进度：0% */
    private static final int DEFAULT_PROGRESS = 0;

    /** 默认租户 ID（DDL NOT NULL DEFAULT 0） */
    private static final long DEFAULT_TENANT_ID = 0L;

    private final OabpJqTodosTaskMapper taskMapper;

    /**
     * 消费任务回写消息，幂等写入 oabp {@code jq_todos_task}。
     *
     * @param message 任务回写消息
     * @throws Exception 写入失败时抛出，由 outbox 重试机制兜底
     */
    @Transactional("oabpTransactionManager")
    public void consume(OabpTaskWritebackMessage message) throws Exception {
        if (message == null || message.getMeetingTodoId() == null) {
            throw new IllegalArgumentException("OabpTaskWritebackMessage.meetingTodoId 不能为空");
        }

        String remark = resolveRemark(message);
        OabpJqTodosTask existing = findExisting(message.getMeetingTodoId());

        if (existing != null) {
            updateTask(existing, message, remark);
            log.info("oabp task writeback UPDATE: meetingTodoId={}, oabpTaskId={}",
                    message.getMeetingTodoId(), existing.getId());
        } else {
            Long newId = insertTask(message, remark);
            log.info("oabp task writeback INSERT: meetingTodoId={}, oabpTaskId={}",
                    message.getMeetingTodoId(), newId);
        }
    }

    /**
     * 按 meeting 待办 ID 查找已存在的 oabp 任务（remark 前缀匹配）。
     *
     * @param meetingTodoId meeting 待办 ID
     * @return 已存在的 oabp 任务；无匹配返回 null
     */
    private OabpJqTodosTask findExisting(String meetingTodoId) {
        String prefix = String.format(REMARK_PREFIX_FORMAT + "%%", meetingTodoId);
        return taskMapper.findByRemarkPrefix(prefix).stream().findFirst().orElse(null);
    }

    /**
     * 解析 remark：消息已提供则用消息值，否则按 {@code [meeting:todoId=xxx]} 格式生成。
     *
     * @param message 消息体
     * @return 最终 remark 值
     */
    private String resolveRemark(OabpTaskWritebackMessage message) {
        if (message.getRemark() != null && !message.getRemark().isBlank()) {
            return message.getRemark();
        }
        return String.format(REMARK_PREFIX_FORMAT, message.getMeetingTodoId());
    }

    /**
     * 插入新任务。
     *
     * @param message 消息体
     * @param remark  最终 remark
     * @return 新任务 ID
     */
    private Long insertTask(OabpTaskWritebackMessage message, String remark) {
        OabpJqTodosTask task = new OabpJqTodosTask();
        task.setTaskName(message.getTaskName());
        task.setBusinessBlock(message.getBusinessBlock() != null ? message.getBusinessBlock() : DEFAULT_BUSINESS_BLOCK);
        task.setDecisionMakerUserId(0L);
        task.setProgress(message.getProgress() != null ? message.getProgress() : DEFAULT_PROGRESS);
        task.setStartDate(resolveStartDate(message));
        task.setPlannedEndDate(resolvePlannedEndDate(message));
        task.setStatus(message.getStatus() != null ? message.getStatus() : DEFAULT_STATUS);
        task.setTaskDetail(message.getTaskDetail());
        task.setRemark(remark);
        task.setDeleted(false);
        task.setTenantId(message.getTenantId() != null ? message.getTenantId() : DEFAULT_TENANT_ID);
        task.setCreator(resolveOperator(message.getSource()));
        task.setUpdater(resolveOperator(message.getSource()));
        taskMapper.insert(task);
        return task.getId();
    }

    /**
     * 更新已存在任务。
     *
     * @param existing 已存在的 oabp 任务
     * @param message  消息体
     * @param remark   最终 remark
     */
    private void updateTask(OabpJqTodosTask existing, OabpTaskWritebackMessage message, String remark) {
        existing.setTaskName(message.getTaskName());
        if (message.getBusinessBlock() != null) {
            existing.setBusinessBlock(message.getBusinessBlock());
        }
        if (message.getProgress() != null) {
            existing.setProgress(message.getProgress());
        }
        if (message.getStartDate() != null) {
            existing.setStartDate(message.getStartDate());
        }
        if (message.getPlannedEndDate() != null) {
            existing.setPlannedEndDate(message.getPlannedEndDate());
        }
        if (message.getStatus() != null) {
            existing.setStatus(message.getStatus());
        }
        if (message.getTaskDetail() != null) {
            existing.setTaskDetail(message.getTaskDetail());
        }
        existing.setRemark(remark);
        existing.setUpdater(resolveOperator(message.getSource()));
        taskMapper.updateById(existing);
    }

    /**
     * 解析开始日期：消息已提供则用消息值，否则回退到计划日期，再回退到当天。
     *
     * @param message 消息体
     * @return 开始日期
     */
    private LocalDate resolveStartDate(OabpTaskWritebackMessage message) {
        if (message.getStartDate() != null) {
            return message.getStartDate();
        }
        if (message.getPlannedEndDate() != null) {
            return message.getPlannedEndDate();
        }
        return LocalDate.now();
    }

    /**
     * 解析计划完成日期：消息已提供则用消息值，否则回退到开始日期，再回退到当天。
     *
     * @param message 消息体
     * @return 计划完成日期
     */
    private LocalDate resolvePlannedEndDate(OabpTaskWritebackMessage message) {
        if (message.getPlannedEndDate() != null) {
            return message.getPlannedEndDate();
        }
        if (message.getStartDate() != null) {
            return message.getStartDate();
        }
        return LocalDate.now();
    }

    /**
     * 解析操作人标识：来源非空时返回 {@code meeting-outbox:<source>}，否则返回固定标识。
     *
     * @param source 触发来源
     * @return 操作人字符串
     */
    private String resolveOperator(String source) {
        return source != null && !source.isBlank() ? "meeting-outbox:" + source : "meeting-outbox";
    }
}

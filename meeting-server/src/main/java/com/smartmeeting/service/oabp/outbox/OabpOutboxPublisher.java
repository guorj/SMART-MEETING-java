package com.smartmeeting.service.oabp.outbox;

import com.smartmeeting.event.DomainEventPublisher;
import com.smartmeeting.event.OabpFollowupSyncRequestedEvent;
import com.smartmeeting.event.OabpSubtaskSyncRequestedEvent;
import com.smartmeeting.event.OabpTaskWritebackRequestedEvent;
import com.smartmeeting.model.OabpFollowupSyncMessage;
import com.smartmeeting.model.OabpSubtaskSyncMessage;
import com.smartmeeting.model.OabpTaskWritebackMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * oabp 跨库事件发布门面：封装常见 meeting → oabp 场景，业务方一行代码触发 outbox。
 *
 * <p>所有方法均在<strong>调用方主库事务内</strong>发布领域事件，由
 * {@link com.smartmeeting.outbox.OabpDomainEventOutboxListener} 在 {@code AFTER_COMMIT} 写 outbox，
 * 最终由 {@link com.smartmeeting.outbox.OutboxPoller} 异步消费写 oabp。</p>
 *
 * <p><b>状态映射</b>：调用方需将 {@code com.smartmeeting.enums.TodoStatus} 转成 oabp 数字状态（0/1/2/3）
 * 后传入本门面；本门面不做枚举翻译，保持与 oabp 数字状态约定一致。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * // 会议结束后回写待办
 * oabpOutboxPublisher.publishTaskWriteback(
 *     meetingTodo.getId(), meetingTodo.getContent(),
 *     meetingTodo.getDeadline(), oabpStatus, progress, null, "meeting-ended");
 * // 周报同步跟进记录（上周进度 + 本周计划）
 * oabpOutboxPublisher.publishFollowupSync(
 *     meetingTodo.getId(), null, "本周已完成",
 *     "上周完成方案设计", "本周推进评审", LocalDate.now(), "weekly-report");
 * }</pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OabpOutboxPublisher {

    private final DomainEventPublisher domainEventPublisher;

    /**
     * 发布任务回写事件（基础方法）。
     *
     * @param meetingTodoId  meeting 待办 ID
     * @param taskName       任务名称
     * @param plannedEndDate 计划完成日期（可空）
     * @param status         oabp 状态 0/1/2/3（可空，默认 0=未开始）
     * @param progress       进度 0-100（可空，默认 0）
     * @param taskDetail     长期任务详情（可空）
     * @param businessBlock  业务板块（可空，默认"未分类"）
     * @param source         触发来源标识
     */
    public void publishTaskWriteback(String meetingTodoId, String taskName,
                                     LocalDate plannedEndDate, Integer status, Integer progress,
                                     String taskDetail, String businessBlock, String source) {
        long sentAt = System.currentTimeMillis();
        OabpTaskWritebackMessage payload = OabpTaskWritebackMessage.builder()
                .meetingTodoId(meetingTodoId)
                .taskName(taskName)
                .plannedEndDate(plannedEndDate)
                .status(status)
                .progress(progress)
                .taskDetail(taskDetail)
                .businessBlock(businessBlock)
                .source(source)
                .sentAt(sentAt)
                .build();
        domainEventPublisher.publish(new OabpTaskWritebackRequestedEvent(meetingTodoId, payload, sentAt));
    }

    /**
     * 发布跟进同步事件（基础方法，周报场景：上周进度 + 本周计划）。
     *
     * @param meetingTodoId     meeting 待办 ID
     * @param oabpTaskId        oabp 任务 ID（可空，消费者按 remark 查找）
     * @param followupContent   任务跟进记录
     * @param lastWeekProgress  上周进度（可空）
     * @param thisWeekPlan      本周计划（可空）
     * @param reportDate        汇报日期（可空，消费者回退到当天）
     * @param source            触发来源标识
     */
    public void publishFollowupSync(String meetingTodoId, Long oabpTaskId,
                                    String followupContent, String lastWeekProgress, String thisWeekPlan,
                                    LocalDate reportDate, String source) {
        long sentAt = System.currentTimeMillis();
        OabpFollowupSyncMessage payload = OabpFollowupSyncMessage.builder()
                .meetingTodoId(meetingTodoId)
                .oabpTaskId(oabpTaskId)
                .followupContent(followupContent)
                .lastWeekProgress(lastWeekProgress)
                .thisWeekPlan(thisWeekPlan)
                .reportDate(reportDate)
                .source(source)
                .sentAt(sentAt)
                .build();
        domainEventPublisher.publish(new OabpFollowupSyncRequestedEvent(meetingTodoId, payload, sentAt));
    }

    /**
     * 发布子任务（执行人）同步事件。
     * <p>
     * 将 meeting 待办责任人同步到 oabp {@code jq_todos_subtask}。消费者按
     * {@code parent_id + asignee_id} 幂等 upsert。
     *
     * @param meetingTodoId    meeting 待办 ID
     * @param oabpTaskId       oabp 主任务 ID（可空，消费者按 remark 前缀查找）
     * @param assigneeOaUserId 责任人 OABP 系统用户 ID（{@code system_users.id}）；
     *                         null 或 {@code <= 0} 时消费者跳过 subtask 同步
     * @param subtaskName      子任务名称（一般取待办内容）
     * @param source           触发来源标识
     */
    public void publishSubtaskSync(String meetingTodoId, Long oabpTaskId, Integer assigneeOaUserId,
                                    String subtaskName, String source) {
        long sentAt = System.currentTimeMillis();
        OabpSubtaskSyncMessage payload = OabpSubtaskSyncMessage.builder()
                .meetingTodoId(meetingTodoId)
                .oabpTaskId(oabpTaskId)
                .assigneeOaUserId(assigneeOaUserId)
                .subtaskName(subtaskName)
                .source(source)
                .sentAt(sentAt)
                .build();
        domainEventPublisher.publish(new OabpSubtaskSyncRequestedEvent(meetingTodoId, payload, sentAt));
    }
}

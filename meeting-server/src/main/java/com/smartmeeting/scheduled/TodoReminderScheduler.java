package com.smartmeeting.scheduled;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmeeting.entity.MeetingTodo;
import com.smartmeeting.enums.TodoStatus;
import com.smartmeeting.repository.TodoMapper;
import com.smartmeeting.service.FeishuTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 待办提醒调度器。
 * <ul>
 *   <li>{@link #scanAndMarkOverdue()}：扫描过 deadline 仍未完成的待办，自动标记 OVERDUE 并提醒责任人。</li>
 *   <li>{@link #escalateOverdueToSupervisor()}：对已 OVERDUE 的待办向责任人直属上级升级提醒。</li>
 *   <li>{@link #pushDailySummary()}：每日向每个责任人推送其未完成待办清单汇总。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "meeting.todo.reminder-enabled", havingValue = "true", matchIfMissing = true)
public class TodoReminderScheduler {

    private final TodoMapper todoMapper;
    private final FeishuTaskService feishuTaskService;

    /** 仍处于「进行中」类、可能变为逾期的状态 */
    private static final List<String> ACTIVE_STATUSES = List.of(
            TodoStatus.PENDING.name(),
            TodoStatus.IN_PROGRESS.name(),
            TodoStatus.DELAYED.name(),
            TodoStatus.BLOCKED.name()
    );

    /**
     * 每 30 分钟扫描一次：将过 deadline 仍未完成的待办自动置为 OVERDUE，并提醒责任人。
     */
    @Scheduled(cron = "${meeting.todo.reminder-cron:0 */30 * * * ?}")
    @Transactional
    public void scanAndMarkOverdue() {
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<MeetingTodo> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(MeetingTodo::getStatus, ACTIVE_STATUSES)
                .isNotNull(MeetingTodo::getDeadline)
                .le(MeetingTodo::getDeadline, now)
                .last("LIMIT 200");
        List<MeetingTodo> rows = todoMapper.selectList(wrapper);
        if (rows.isEmpty()) {
            return;
        }
        int marked = 0;
        for (MeetingTodo todo : rows) {
            try {
                todo.setStatus(TodoStatus.OVERDUE.name());
                todo.setLastRemindAt(now);
                Integer rc = todo.getRemindCount() == null ? 0 : todo.getRemindCount();
                todo.setRemindCount(rc + 1);
                todoMapper.updateById(todo);
                feishuTaskService.remindTodoOwner(todo);
                marked++;
            } catch (Exception e) {
                log.warn("Failed to mark todo overdue: todoId={}, err={}", todo.getId(), e.getMessage());
            }
        }
        log.info("Todo overdue scan: marked {} of {} items as OVERDUE", marked, rows.size());
    }

    /**
     * 每日两次（08:30 与 17:00）：对已 OVERDUE 的待办向责任人直属上级发送升级提醒。
     * <p>
     * 按责任人聚合，每个责任人仅发送一张卡片（含其所有逾期待办），避免每小时打扰。
     */
    @Scheduled(cron = "${meeting.todo.escalation-cron:0 30 8,17 * * ?}")
    @Transactional
    public void escalateOverdueToSupervisor() {
        LambdaQueryWrapper<MeetingTodo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MeetingTodo::getStatus, TodoStatus.OVERDUE.name())
                .last("LIMIT 500");
        List<MeetingTodo> rows = todoMapper.selectList(wrapper);
        if (rows.isEmpty()) {
            return;
        }
        // 按 assigneeId 聚合，每个责任人只取首条用于定位上级（卡片内会聚合该责任人所有逾期项）
        Map<String, MeetingTodo> byAssignee = new HashMap<>();
        for (MeetingTodo todo : rows) {
            String assigneeId = todo.getAssigneeId();
            if (assigneeId == null || assigneeId.isBlank() || "unknown".equalsIgnoreCase(assigneeId)) {
                continue;
            }
            byAssignee.putIfAbsent(assigneeId, todo);
        }
        int escalated = 0;
        for (Map.Entry<String, MeetingTodo> entry : byAssignee.entrySet()) {
            try {
                boolean ok = feishuTaskService.escalateOverdueToSupervisor(entry.getValue());
                if (ok) {
                    escalated++;
                }
            } catch (Exception e) {
                log.warn("Failed to escalate overdue todo to supervisor: assignee={}, err={}",
                        entry.getKey(), e.getMessage());
            }
        }
        log.info("Todo escalation scan: notified supervisors for {} of {} assignees (of {} overdue items)",
                escalated, byAssignee.size(), rows.size());
    }

    /**
     * 每日 09:00 推送：按责任人聚合其所有未完成待办，发送每日清单汇总卡片。
     */
    @Scheduled(cron = "${meeting.todo.daily-summary-cron:0 0 9 * * ?}")
    @Transactional
    public void pushDailySummary() {
        LambdaQueryWrapper<MeetingTodo> wrapper = new LambdaQueryWrapper<>();
        wrapper.ne(MeetingTodo::getStatus, TodoStatus.COMPLETED.name())
                .last("LIMIT 1000");
        List<MeetingTodo> rows = todoMapper.selectList(wrapper);
        if (rows.isEmpty()) {
            log.info("Daily todo summary: no open todos, skip");
            return;
        }
        // 按责任人飞书 user_id 聚合
        Map<String, List<MeetingTodo>> byAssignee = new HashMap<>();
        for (MeetingTodo todo : rows) {
            String assigneeId = todo.getAssigneeId();
            if (assigneeId == null || assigneeId.isBlank() || "unknown".equalsIgnoreCase(assigneeId)) {
                continue;
            }
            byAssignee.computeIfAbsent(assigneeId, k -> new ArrayList<>()).add(todo);
        }
        int pushed = 0;
        for (Map.Entry<String, List<MeetingTodo>> entry : byAssignee.entrySet()) {
            String assigneeId = entry.getKey();
            List<MeetingTodo> todos = entry.getValue();
            try {
                feishuTaskService.pushDailySummary(assigneeId, todos, LocalDate.now());
                pushed++;
            } catch (Exception e) {
                log.warn("Failed to push daily summary: assignee={}, err={}", assigneeId, e.getMessage());
            }
        }
        log.info("Daily todo summary: pushed to {} assignees (of {} open todos)", pushed, rows.size());
    }
}

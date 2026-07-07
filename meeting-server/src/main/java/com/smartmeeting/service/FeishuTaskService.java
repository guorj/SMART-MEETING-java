package com.smartmeeting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.entity.MeetingTodo;
import com.smartmeeting.entity.UserMapping;
import com.smartmeeting.enums.TodoStatus;
import com.smartmeeting.repository.MeetingMapper;
import com.smartmeeting.repository.TodoMapper;
import com.smartmeeting.repository.UserMappingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeishuTaskService {

    private final FeishuService feishuService;
    private final MeetingMapper meetingMapper;
    private final FeishuCardBuilder cardBuilder;
    private final UserMappingMapper userMappingMapper;
    private final TodoMapper todoMapper;

    @Value("${meeting.base-url:http://localhost:8765}")
    private String baseUrl;

    public void syncTodosToFeishu(String meetingId, List<MeetingTodo> todos) {
        Meeting meeting = meetingMapper.selectById(meetingId);
        if (meeting == null || todos == null || todos.isEmpty()) {
            return;
        }
        String dashboardHint = baseUrl + "/dashboard.html";
        for (MeetingTodo todo : todos) {
            notifyTodoStakeholders(meeting, todo, dashboardHint);
        }
        String summary = "会议【" + meeting.getTitle() + "】待办已同步，共 " + todos.size() + " 项。";
        if (meeting.getChatId() != null && !meeting.getChatId().isBlank()) {
            feishuService.sendMessage(meeting.getChatId(), summary);
        }
    }

    private void notifyTodoStakeholders(Meeting meeting, MeetingTodo todo, String dashboardHint) {
        String assigneeId = todo.getAssigneeId();
        if (assigneeId != null && !assigneeId.isBlank() && !"unknown".equalsIgnoreCase(assigneeId)) {
            String cardJson = cardBuilder.buildTodoActionCard(todo);
            feishuService.sendInteractiveCardToUserId(assigneeId, cardJson);
        }
        String operatorId = todo.getOperatorId();
        if (operatorId != null && !operatorId.isBlank() && !"unknown".equalsIgnoreCase(operatorId)
                && !operatorId.equals(assigneeId)) {
            String text = "您有一项待办需经办：【" + todo.getContent() + "】\n"
                    + "请在智能会议前台「我的待办」更新进度或上传附件：\n" + dashboardHint;
            feishuService.sendMessageToUserId(operatorId, text);
        }
    }

    public void remindTodoOwner(MeetingTodo todo) {
        if (todo == null || todo.getAssigneeId() == null || todo.getAssigneeId().isBlank()) {
            return;
        }
        if (!"unknown".equalsIgnoreCase(todo.getAssigneeId())) {
            feishuService.sendInteractiveCardToUserId(todo.getAssigneeId(), cardBuilder.buildTodoActionCard(todo));
        }
        log.info("Todo reminder sent: todoId={}, assignee={}", todo.getId(), todo.getAssigneeId());
    }

    /**
     * 向责任人直属上级发送延期升级提醒。
     * <p>
     * 聚合该责任人所有 OVERDUE 待办，发送交互式卡片，上级可选择「催办」或「已知晓暂不处理」。
     *
     * @param todo 已逾期的待办（用于定位责任人与上级）
     * @return 成功发送给上级返回 {@code true}；无上级映射或发送失败返回 {@code false}
     */
    public boolean escalateOverdueToSupervisor(MeetingTodo todo) {
        if (todo == null) {
            return false;
        }
        String assigneeId = todo.getAssigneeId();
        if (assigneeId == null || assigneeId.isBlank() || "unknown".equalsIgnoreCase(assigneeId)) {
            return false;
        }
        UserMapping mapping = userMappingMapper.selectOne(new LambdaQueryWrapper<UserMapping>()
                .eq(UserMapping::getFeishuUserId, assigneeId)
                .last("LIMIT 1"));
        if (mapping == null) {
            log.debug("Escalation skipped: no user mapping for assignee {}", assigneeId);
            return false;
        }
        String supervisorId = mapping.getSupervisorFeishuUserId();
        if (supervisorId == null || supervisorId.isBlank() || "unknown".equalsIgnoreCase(supervisorId)) {
            log.debug("Escalation skipped: no supervisor configured for assignee {}", assigneeId);
            return false;
        }
        // 聚合该责任人所有 OVERDUE 待办，一次性向上级展示
        LambdaQueryWrapper<MeetingTodo> w = new LambdaQueryWrapper<>();
        w.eq(MeetingTodo::getAssigneeId, assigneeId)
                .eq(MeetingTodo::getStatus, TodoStatus.OVERDUE.name())
                .last("LIMIT 50");
        List<MeetingTodo> overdueTodos = todoMapper.selectList(w);
        if (overdueTodos.isEmpty()) {
            return false;
        }
        String supervisorName = feishuService.getUserNameByUserId(supervisorId);
        String cardJson = cardBuilder.buildSupervisorEscalationCard(
                supervisorName, safeName(todo.getAssigneeName(), assigneeId), overdueTodos);
        boolean ok = feishuService.sendInteractiveCardToUserId(supervisorId, cardJson);
        if (ok) {
            log.info("Escalation card sent: assignee={}, supervisor={}, overdueCount={}",
                    assigneeId, supervisorId, overdueTodos.size());
        }
        return ok;
    }

    /**
     * 向指定责任人推送每日未完成待办清单汇总（交互式卡片）。
     * <p>
     * 卡片列出每条待办的内容、状态、截止时间，并提供「申请延期」「挂起」按钮；
     * 责任人点击按钮后，机器人引导其回复理由文本完成状态变更。
     *
     * @param assigneeId 责任人飞书 user_id
     * @param todos      该责任人未完成的待办列表
     * @param date       汇总日期
     */
    public void pushDailySummary(String assigneeId, List<MeetingTodo> todos, LocalDate date) {
        if (assigneeId == null || assigneeId.isBlank() || "unknown".equalsIgnoreCase(assigneeId)) {
            return;
        }
        if (todos == null || todos.isEmpty()) {
            return;
        }
        String assigneeName = todos.get(0).getAssigneeName();
        if (assigneeName == null || assigneeName.isBlank()) {
            assigneeName = feishuService.getUserNameByUserId(assigneeId);
        }
        String cardJson = cardBuilder.buildDailySummaryCard(assigneeName, todos, date);
        feishuService.sendInteractiveCardToUserId(assigneeId, cardJson);
        log.info("Daily todo summary card sent: assignee={}, count={}", assigneeId, todos.size());
    }

    /**
     * 向决策人推送裁决卡片（责任人已提交完成，等待决策人裁决）。
     * <p>
     * 决策人飞书 user_id 从 {@code todo.decisionMakerFeishuUserId} 读取（由
     * {@code TodoService.applyAssigneeComplete} 缓存）。
     * </p>
     *
     * @param todo 已进入 PENDING_DECISION 态的待办
     */
    public void notifyDecisionMaker(MeetingTodo todo) {
        if (todo == null) {
            return;
        }
        String decisionMakerId = todo.getDecisionMakerFeishuUserId();
        if (decisionMakerId == null || decisionMakerId.isBlank()
                || "unknown".equalsIgnoreCase(decisionMakerId)) {
            log.warn("notifyDecisionMaker skipped: no decisionMakerFeishuUserId, todoId={}", todo.getId());
            return;
        }
        String cardJson = cardBuilder.buildDecisionMakerCard(todo);
        boolean ok = feishuService.sendInteractiveCardToUserId(decisionMakerId, cardJson);
        if (ok) {
            log.info("Decision maker card sent: todoId={}, decisionMaker={}", todo.getId(), decisionMakerId);
        }
    }

    /**
     * 通知责任人裁决结果。
     * <p>
     * 决策人作出裁决后，向责任人发送文本消息告知结果（完成/延期/驳回）。
     * </p>
     *
     * @param todo 已裁决的待办（须已写入 decisionResult / decisionNote）
     */
    public void notifyAssigneeDecisionResult(MeetingTodo todo) {
        if (todo == null || todo.getAssigneeId() == null || todo.getAssigneeId().isBlank()
                || "unknown".equalsIgnoreCase(todo.getAssigneeId())) {
            return;
        }
        String result = todo.getDecisionResult() == null ? "未知" : todo.getDecisionResult();
        String resultLabel = switch (result) {
            case "APPROVED" -> "已裁决完成";
            case "DELAYED" -> "已裁决延期";
            case "REJECTED" -> "已驳回（回到进行中）";
            default -> result;
        };
        StringBuilder text = new StringBuilder();
        text.append("⚖️ 您的待办【").append(safe(todo.getContent())).append("】").append(resultLabel).append("。\n");
        if (todo.getDecisionMakerName() != null && !todo.getDecisionMakerName().isBlank()) {
            text.append("决策人：").append(todo.getDecisionMakerName()).append("\n");
        }
        if (todo.getDecisionNote() != null && !todo.getDecisionNote().isBlank()) {
            text.append("裁决备注：").append(todo.getDecisionNote());
        }
        feishuService.sendMessageToUserId(todo.getAssigneeId(), text.toString());
        log.info("Decision result notified: todoId={}, assignee={}, result={}",
                todo.getId(), todo.getAssigneeId(), result);
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private String safeName(String name, String fallback) {
        if (name != null && !name.isBlank()) {
            return name;
        }
        return fallback == null ? "未知" : fallback;
    }
}

package com.smartmeeting.service;

import com.smartmeeting.entity.MeetingTodo;
import com.smartmeeting.enums.TodoAuthorRole;
import com.smartmeeting.enums.TodoStatus;
import com.smartmeeting.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 待办权限校验：责任人可完成/挂起；经办人仅可更新进度与附件。
 */
@Service
public class TodoPermissionService {

  private static final Set<String> ASSIGNEE_TERMINAL_TARGETS = Set.of(
      TodoStatus.COMPLETED.name(),
      TodoStatus.BLOCKED.name(),
      TodoStatus.DELAYED.name()
  );

  /**
   * 解析当前用户对某待办的角色。
   *
   * @param todo         待办
   * @param feishuUserId 飞书 user_id
   * @return 角色；无权限时返回 null
   */
  public TodoAuthorRole resolveRole(MeetingTodo todo, String feishuUserId) {
    if (todo == null || feishuUserId == null || feishuUserId.isBlank()) {
      return null;
    }
    if (matchesUser(todo.getAssigneeId(), feishuUserId)) {
      return TodoAuthorRole.ASSIGNEE;
    }
    if (matchesUser(effectiveOperatorId(todo), feishuUserId)
        && !matchesUser(todo.getAssigneeId(), feishuUserId)) {
      return TodoAuthorRole.OPERATOR;
    }
    return null;
  }

  /**
   * 要求调用者为责任人。
   */
  public void requireAssignee(MeetingTodo todo, String feishuUserId) {
    if (!matchesUser(todo.getAssigneeId(), feishuUserId)) {
      throw new BusinessException(403, "仅责任人可执行此操作");
    }
  }

  /**
   * 要求调用者为决策人（二段式裁决权限校验）。
   * <p>
   * 决策人飞书 user_id 缓存在 {@code todo.decisionMakerFeishuUserId}；
   * 未设置（待办未进入裁决态）时拒绝所有调用。
   * </p>
   *
   * @param todo         待办
   * @param feishuUserId 操作人飞书 user_id
   */
  public void requireDecisionMaker(MeetingTodo todo, String feishuUserId) {
    if (todo == null || feishuUserId == null || feishuUserId.isBlank()) {
      throw new BusinessException(403, "仅决策人可执行此操作");
    }
    if (!matchesUser(todo.getDecisionMakerFeishuUserId(), feishuUserId)) {
      throw new BusinessException(403, "仅决策人可执行此操作");
    }
  }

  /**
   * 要求调用者为责任人或经办人。
   */
  public void requireAssigneeOrOperator(MeetingTodo todo, String feishuUserId) {
    if (resolveRole(todo, feishuUserId) == null) {
      throw new BusinessException(403, "仅责任人或经办人可执行此操作");
    }
  }

  /**
   * 校验责任人发起的状态流转是否合法。
   */
  public void validateAssigneeStatusTransition(String oldStatus, TodoStatus newStatus) {
    if (newStatus == TodoStatus.OVERDUE) {
      throw new BusinessException(400, "OVERDUE 状态由系统维护，不可手动设置");
    }
    String old = normalizeStatus(oldStatus);
    String target = newStatus.name();

    if (old.equals(target)) {
      return;
    }

    // PENDING_DECISION 是裁决中间态，责任人不可直接改出（须由决策人走 applyDecisionMakerDecision）
    if (TodoStatus.PENDING_DECISION.name().equals(old)) {
      throw new BusinessException(400, "待裁决态不可由责任人直接变更，须由决策人裁决");
    }

    if (TodoStatus.COMPLETED.name().equals(target)) {
      if (!Set.of(TodoStatus.PENDING.name(), TodoStatus.IN_PROGRESS.name(),
          TodoStatus.DELAYED.name(), TodoStatus.OVERDUE.name(), TodoStatus.BLOCKED.name()).contains(old)) {
        throw new BusinessException(400, "当前状态不可标记为完成: " + old);
      }
      return;
    }

    if (TodoStatus.BLOCKED.name().equals(target)) {
      if (!Set.of(TodoStatus.PENDING.name(), TodoStatus.IN_PROGRESS.name()).contains(old)) {
        throw new BusinessException(400, "当前状态不可挂起: " + old);
      }
      return;
    }

    if (TodoStatus.DELAYED.name().equals(target)) {
      if (!Set.of(TodoStatus.PENDING.name(), TodoStatus.IN_PROGRESS.name(), TodoStatus.BLOCKED.name()).contains(old)) {
        throw new BusinessException(400, "当前状态不可标记延期: " + old);
      }
      return;
    }

    if (Set.of(TodoStatus.PENDING.name(), TodoStatus.IN_PROGRESS.name()).contains(target)) {
      if (!Set.of(TodoStatus.COMPLETED.name(), TodoStatus.BLOCKED.name(),
          TodoStatus.DELAYED.name(), TodoStatus.OVERDUE.name()).contains(old)) {
        throw new BusinessException(400, "当前状态不可回退为: " + target);
      }
      return;
    }

    throw new BusinessException(400, "不允许的状态流转: " + old + " -> " + target);
  }

  /**
   * 经办人是否可更新进度（不可改终态状态）。
   */
  public boolean operatorMayUpdateProgress(MeetingTodo todo, String feishuUserId) {
    return resolveRole(todo, feishuUserId) != null;
  }

  public String effectiveOperatorId(MeetingTodo todo) {
    if (todo.getOperatorId() != null && !todo.getOperatorId().isBlank()
        && !"unknown".equalsIgnoreCase(todo.getOperatorId())) {
      return todo.getOperatorId();
    }
    return todo.getAssigneeId();
  }

  private boolean matchesUser(String storedId, String feishuUserId) {
    return storedId != null && !storedId.isBlank()
        && !"unknown".equalsIgnoreCase(storedId)
        && storedId.equals(feishuUserId);
  }

  private String normalizeStatus(String status) {
    if (status == null || status.isBlank()) {
      return TodoStatus.PENDING.name();
    }
    return status.trim();
  }
}

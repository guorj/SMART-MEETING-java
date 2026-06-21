package com.smartmeeting.service;

import com.smartmeeting.entity.MeetingTodo;
import com.smartmeeting.enums.TodoAuthorRole;
import com.smartmeeting.enums.TodoStatus;
import com.smartmeeting.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TodoPermissionServiceTest {

    private TodoPermissionService service;

    @BeforeEach
    void setUp() {
        service = new TodoPermissionService();
    }

  @Test
  void resolveRole_assignee() {
    MeetingTodo todo = todo("a1", "o1");
    assertEquals(TodoAuthorRole.ASSIGNEE, service.resolveRole(todo, "a1"));
  }

  @Test
  void resolveRole_operatorOnly() {
    MeetingTodo todo = todo("a1", "o2");
    assertEquals(TodoAuthorRole.OPERATOR, service.resolveRole(todo, "o2"));
  }

  @Test
  void requireAssignee_rejectsOperator() {
    MeetingTodo todo = todo("a1", "o2");
    assertThrows(BusinessException.class, () -> service.requireAssignee(todo, "o2"));
  }

  @Test
  void validateAssigneeStatusTransition_blocksOverdueManual() {
    assertThrows(BusinessException.class,
        () -> service.validateAssigneeStatusTransition("PENDING", TodoStatus.OVERDUE));
  }

  @Test
  void validateAssigneeStatusTransition_allowsCompleteFromPending() {
    service.validateAssigneeStatusTransition("PENDING", TodoStatus.COMPLETED);
  }

  private MeetingTodo todo(String assigneeId, String operatorId) {
    MeetingTodo t = new MeetingTodo();
    t.setAssigneeId(assigneeId);
    t.setAssigneeName("A");
    t.setOperatorId(operatorId);
    t.setOperatorName("O");
    t.setStatus(TodoStatus.PENDING.name());
    return t;
  }
}

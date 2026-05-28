package com.smartmeeting.scheduled;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmeeting.entity.MeetingTodo;
import com.smartmeeting.repository.TodoMapper;
import com.smartmeeting.service.FeishuTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TodoReminderScheduler {

    private final TodoMapper todoMapper;
    private final FeishuTaskService feishuTaskService;

    @Scheduled(cron = "${meeting.todo.reminder-cron:0 */30 * * * ?}")
    @Transactional
    public void scanAndRemind() {
        LambdaQueryWrapper<MeetingTodo> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(MeetingTodo::getStatus, List.of("PENDING", "IN_PROGRESS", "DELAYED", "BLOCKED", "OVERDUE"))
                .le(MeetingTodo::getDeadline, LocalDateTime.now())
                .last("LIMIT 100");
        List<MeetingTodo> rows = todoMapper.selectList(wrapper);
        for (MeetingTodo todo : rows) {
            feishuTaskService.remindTodoOwner(todo);
            todo.setLastRemindAt(LocalDateTime.now());
            Integer rc = todo.getRemindCount() == null ? 0 : todo.getRemindCount();
            todo.setRemindCount(rc + 1);
            todoMapper.updateById(todo);
        }
        if (!rows.isEmpty()) {
            log.info("Todo reminder scheduler processed {} overdue items", rows.size());
        }
    }
}

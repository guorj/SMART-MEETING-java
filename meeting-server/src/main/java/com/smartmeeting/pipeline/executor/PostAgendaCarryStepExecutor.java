package com.smartmeeting.pipeline.executor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.entity.MeetingTodo;
import com.smartmeeting.pipeline.StepExecutionContext;
import com.smartmeeting.pipeline.StepExecutionResult;
import com.smartmeeting.pipeline.StepExecutor;
import com.smartmeeting.repository.MeetingMapper;
import com.smartmeeting.repository.TodoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PostAgendaCarryStepExecutor implements StepExecutor {

    private final MeetingMapper meetingMapper;
    private final TodoMapper todoMapper;
    private final ObjectMapper objectMapper;

    @Override
    public String stepType() {
        return "post-agenda-carry";
    }

    @Override
    public StepExecutionResult execute(StepExecutionContext context) throws Exception {
        Meeting next = meetingMapper.selectOne(new LambdaQueryWrapper<Meeting>()
                .eq(Meeting::getPreviousMeetingId, context.getMeetingId())
                .last("LIMIT 1"));
        if (next == null) {
            return StepExecutionResult.ok("no-next-meeting", "{\"carried\":0}");
        }
        List<MeetingTodo> pending = todoMapper.selectList(new LambdaQueryWrapper<MeetingTodo>()
                .eq(MeetingTodo::getMeetingId, context.getMeetingId())
                .in(MeetingTodo::getStatus, List.of("PENDING", "IN_PROGRESS", "DELAYED", "BLOCKED", "OVERDUE")));
        if (pending.isEmpty()) {
            return StepExecutionResult.ok("no-pending-todos", "{\"carried\":0}");
        }
        List<String> agenda = new ArrayList<>();
        if (next.getAgenda() != null && !next.getAgenda().isBlank()) {
            try {
                agenda.addAll(objectMapper.readValue(next.getAgenda(), new TypeReference<List<String>>() {}));
            } catch (Exception ignored) {
                // ignore invalid agenda json
            }
        }
        for (MeetingTodo t : pending) {
            agenda.add("【上次待办续报】" + t.getContent() + "（责任人：" + safe(t.getAssigneeName()) + "）");
        }
        next.setAgenda(objectMapper.writeValueAsString(agenda));
        meetingMapper.updateById(next);
        return StepExecutionResult.ok("agenda-carried", "{\"carried\":" + pending.size() + ",\"nextMeetingId\":\"" + next.getId() + "\"}");
    }

    private String safe(String s) {
        return s == null || s.isBlank() ? "未指派" : s;
    }
}


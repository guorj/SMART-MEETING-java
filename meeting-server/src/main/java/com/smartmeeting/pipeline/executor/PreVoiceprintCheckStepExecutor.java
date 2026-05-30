package com.smartmeeting.pipeline.executor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.entity.Participant;
import com.smartmeeting.pipeline.StepExecutionContext;
import com.smartmeeting.pipeline.StepExecutionResult;
import com.smartmeeting.pipeline.StepExecutor;
import com.smartmeeting.repository.ParticipantMapper;
import com.smartmeeting.service.FeishuService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PreVoiceprintCheckStepExecutor implements StepExecutor {

    private final ParticipantMapper participantMapper;
    private final FeishuService feishuService;

    @Override
    public String stepType() {
        return "pre-voiceprint-check";
    }

    @Override
    public StepExecutionResult execute(StepExecutionContext context) {
        Meeting meeting = context.getMeeting();
        if (meeting == null || meeting.getChatId() == null || meeting.getChatId().isBlank()) {
            return StepExecutionResult.failed("meeting/chatId missing");
        }
        List<Participant> participants = participantMapper.selectList(new LambdaQueryWrapper<Participant>()
                .eq(Participant::getMeetingId, meeting.getId()));
        long unready = participants.stream().filter(p -> !Boolean.TRUE.equals(p.getVoiceprintReady())).count();
        if (unready <= 0) {
            return StepExecutionResult.ok("voiceprint-all-ready", "{\"unready\":0}");
        }
        String text = "会前声纹检查：有 " + unready + " 位参会人声纹未就绪，请及时补录。";
        boolean ok = feishuService.sendMessage(meeting.getChatId(), text);
        return StepExecutionResult.ok(ok ? "voiceprint-warning-pushed" : "voiceprint-warning-failed",
                "{\"unready\":" + unready + "}");
    }
}


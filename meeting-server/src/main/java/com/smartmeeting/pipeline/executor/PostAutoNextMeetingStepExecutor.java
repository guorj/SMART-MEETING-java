package com.smartmeeting.pipeline.executor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmeeting.api.dto.MeetingCreateRequest;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.entity.Participant;
import com.smartmeeting.pipeline.StepExecutionContext;
import com.smartmeeting.pipeline.StepExecutionResult;
import com.smartmeeting.pipeline.StepExecutor;
import com.smartmeeting.repository.MeetingMapper;
import com.smartmeeting.repository.ParticipantMapper;
import com.smartmeeting.service.MeetingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PostAutoNextMeetingStepExecutor implements StepExecutor {

    private final MeetingMapper meetingMapper;
    private final ParticipantMapper participantMapper;
    private final MeetingService meetingService;
    private final PipelineExecutorSupport support;

    @Override
    public String stepType() {
        return "post-auto-next-meeting";
    }

    @Override
    public StepExecutionResult execute(StepExecutionContext context) {
        Meeting current = context.getMeeting();
        if (current == null) {
            return StepExecutionResult.failed("meeting missing");
        }
        Meeting exists = meetingMapper.selectOne(new LambdaQueryWrapper<Meeting>()
                .eq(Meeting::getPreviousMeetingId, current.getId())
                .last("LIMIT 1"));
        if (exists != null) {
            return StepExecutionResult.ok("next-meeting-exists", "{\"nextMeetingId\":\"" + exists.getId() + "\"}");
        }
        int days = support.number(support.parseConfig(context.getStep().getConfigJson()), "days", 7);
        MeetingCreateRequest req = new MeetingCreateRequest();
        req.setTitle(current.getTitle());
        req.setCompany(current.getCompany());
        req.setDepartment(current.getDepartment());
        req.setGroupName(current.getGroupName());
        req.setPresetTypeCode(current.getPresetTypeCode());
        req.setCreatorId(current.getCreatorId());
        req.setChatId(current.getChatId());
        req.setPreviousMeetingId(current.getId());
        req.setScheduledTime(LocalDateTime.now().plusDays(Math.max(1, days)));
        req.setParticipants(copyParticipants(current.getId()));
        String nextMeetingId = meetingService.createMeeting(req).getId();
        return StepExecutionResult.ok("next-meeting-created", "{\"nextMeetingId\":\"" + nextMeetingId + "\"}");
    }

    private List<MeetingCreateRequest.ParticipantEntry> copyParticipants(String meetingId) {
        List<Participant> rows = participantMapper.selectList(new LambdaQueryWrapper<Participant>()
                .eq(Participant::getMeetingId, meetingId));
        List<MeetingCreateRequest.ParticipantEntry> out = new ArrayList<>();
        for (Participant p : rows) {
            MeetingCreateRequest.ParticipantEntry e = new MeetingCreateRequest.ParticipantEntry();
            e.setUserId(p.getUserId());
            e.setName(p.getName());
            e.setAttendanceMode(p.getAttendanceMode());
            out.add(e);
        }
        return out;
    }
}


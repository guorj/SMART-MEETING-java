package com.smartmeeting.statemachine;

import com.smartmeeting.entity.Meeting;
import com.smartmeeting.enums.MeetingStatus;
import com.smartmeeting.exception.BusinessException;
import com.smartmeeting.repository.MeetingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.StateMachineEventResult;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingStateMachineService {

    private final MeetingMapper meetingMapper;
    private final StateMachineFactory<MeetingStatus, MeetingEvent> stateMachineFactory;

    public MeetingStatus apply(String meetingId, MeetingEvent event) {
        Meeting meeting = meetingMapper.selectById(meetingId);
        if (meeting == null) {
            throw new BusinessException(404, "会议不存在: " + meetingId);
        }

        MeetingStatus current = safeParseStatus(meeting.getStatus());
        StateMachine<MeetingStatus, MeetingEvent> sm = stateMachineFactory.getStateMachine(meetingId);
        sm.stopReactively().block();
        sm.getStateMachineAccessor().doWithAllRegions(accessor ->
                accessor.resetStateMachineReactively(
                        new DefaultStateMachineContext<>(current, null, null, null)).block());
        sm.startReactively().block();

        List<StateMachineEventResult<MeetingStatus, MeetingEvent>> results = sm.sendEvent(Mono.just(
                        MessageBuilder.withPayload(event)
                                .setHeader("meetingId", meetingId)
                                .build()))
                .collectList()
                .block();
        boolean accepted = results != null
                && results.stream().anyMatch(r -> r.getResultType() == StateMachineEventResult.ResultType.ACCEPTED);
        if (!accepted) {
            throw new BusinessException(400, "状态流转不合法: " + current + " -> " + event);
        }
        MeetingStatus target = sm.getState().getId();
        meeting.setStatus(target.name());
        meetingMapper.updateById(meeting);
        log.info("Meeting state changed by statemachine: meetingId={}, {} --{}--> {}",
                meetingId, current, event, target);
        return target;
    }

    public void forceStatus(String meetingId, MeetingStatus status) {
        Meeting meeting = new Meeting();
        meeting.setId(meetingId);
        meeting.setStatus(status.name());
        meetingMapper.updateById(meeting);
    }

    private MeetingStatus safeParseStatus(String status) {
        try {
            return MeetingStatus.valueOf(status);
        } catch (Exception e) {
            return MeetingStatus.ISSUE_COLLECTING;
        }
    }
}

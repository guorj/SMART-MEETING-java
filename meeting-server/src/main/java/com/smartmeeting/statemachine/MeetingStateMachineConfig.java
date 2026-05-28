package com.smartmeeting.statemachine;

import com.smartmeeting.enums.MeetingStatus;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.EnumStateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineConfigurationConfigurer;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;

import java.util.EnumSet;

@Configuration
@EnableStateMachineFactory
public class MeetingStateMachineConfig extends EnumStateMachineConfigurerAdapter<MeetingStatus, MeetingEvent> {

    @Override
    public void configure(StateMachineConfigurationConfigurer<MeetingStatus, MeetingEvent> config) throws Exception {
        config.withConfiguration().autoStartup(false);
    }

    @Override
    public void configure(StateMachineStateConfigurer<MeetingStatus, MeetingEvent> states) throws Exception {
        states.withStates()
                .initial(MeetingStatus.ISSUE_COLLECTING)
                .states(EnumSet.allOf(MeetingStatus.class));
    }

    @Override
    public void configure(StateMachineTransitionConfigurer<MeetingStatus, MeetingEvent> transitions) throws Exception {
        transitions
                .withExternal().source(MeetingStatus.ISSUE_COLLECTING).target(MeetingStatus.INVITED).event(MeetingEvent.INVITE_PARTICIPANTS)
                .and()
                .withExternal().source(MeetingStatus.ISSUE_COLLECTING).target(MeetingStatus.STARTED).event(MeetingEvent.FAST_START)
                .and()
                .withExternal().source(MeetingStatus.INVITED).target(MeetingStatus.STARTED).event(MeetingEvent.START_MEETING)
                .and()
                .withExternal().source(MeetingStatus.STARTED).target(MeetingStatus.RECORDING).event(MeetingEvent.START_RECORDING)
                .and()
                .withExternal().source(MeetingStatus.RECORDING).target(MeetingStatus.PAUSED).event(MeetingEvent.PAUSE_RECORDING)
                .and()
                .withExternal().source(MeetingStatus.PAUSED).target(MeetingStatus.RECORDING).event(MeetingEvent.RESUME_RECORDING)
                .and()
                .withExternal().source(MeetingStatus.STARTED).target(MeetingStatus.PROCESSING).event(MeetingEvent.END_MEETING)
                .and()
                .withExternal().source(MeetingStatus.RECORDING).target(MeetingStatus.PROCESSING).event(MeetingEvent.END_MEETING)
                .and()
                .withExternal().source(MeetingStatus.PAUSED).target(MeetingStatus.PROCESSING).event(MeetingEvent.END_MEETING)
                .and()
                .withExternal().source(MeetingStatus.PROCESSING).target(MeetingStatus.COMPLETED).event(MeetingEvent.MINUTE_READY)
                .and()
                .withExternal().source(MeetingStatus.COMPLETED).target(MeetingStatus.TODO_TRACKING).event(MeetingEvent.TODO_SYNCED)
                .and()
                .withExternal().source(MeetingStatus.TODO_TRACKING).target(MeetingStatus.ALL_DONE).event(MeetingEvent.ALL_TODOS_DONE)
                .and()
                .withExternal().source(MeetingStatus.ALL_DONE).target(MeetingStatus.ARCHIVED).event(MeetingEvent.ARCHIVE)
                .and()
                .withExternal().source(MeetingStatus.ARCHIVED).target(MeetingStatus.CLOSED).event(MeetingEvent.CLOSE_MEETING)
                .and()
                .withExternal().source(MeetingStatus.ISSUE_COLLECTING).target(MeetingStatus.CANCELLED).event(MeetingEvent.CANCEL_MEETING)
                .and()
                .withExternal().source(MeetingStatus.INVITED).target(MeetingStatus.CANCELLED).event(MeetingEvent.CANCEL_MEETING)
                .and()
                .withExternal().source(MeetingStatus.STARTED).target(MeetingStatus.ABORTED).event(MeetingEvent.ABORT_MEETING)
                .and()
                .withExternal().source(MeetingStatus.RECORDING).target(MeetingStatus.ABORTED).event(MeetingEvent.ABORT_MEETING)
                .and()
                .withExternal().source(MeetingStatus.PAUSED).target(MeetingStatus.ABORTED).event(MeetingEvent.ABORT_MEETING)
                .and()
                .withExternal().source(MeetingStatus.PROCESSING).target(MeetingStatus.ABORTED).event(MeetingEvent.ABORT_MEETING);
    }
}

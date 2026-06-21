package com.smartmeeting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.entity.Participant;
import com.smartmeeting.repository.ParticipantMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalendarAttendeeResolverTest {

    @Mock
    private ParticipantMapper participantMapper;
    @Mock
    private FeishuUserIdResolver feishuUserIdResolver;

    private CalendarAttendeeResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new CalendarAttendeeResolver(participantMapper, feishuUserIdResolver, new ObjectMapper());
    }

    @Test
    void resolve_mapsPresetParticipantsByNameWhenUserIdIsPlaceholder() {
        Meeting meeting = new Meeting();
        meeting.setId("m1");
        meeting.setCreatorId("b7319b67");

        Participant p1 = new Participant();
        p1.setUserId("vp_placeholder1");
        p1.setName("李金杰");
        Participant p2 = new Participant();
        p2.setUserId("vp_placeholder2");
        p2.setName("田林源");
        when(participantMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(p1, p2));
        when(feishuUserIdResolver.resolveParticipant("vp_placeholder1", "李金杰"))
                .thenReturn(Optional.of("8813018f"));
        when(feishuUserIdResolver.resolveParticipant("vp_placeholder2", "田林源"))
                .thenReturn(Optional.of("uid-tian"));
        when(feishuUserIdResolver.resolve("b7319b67")).thenReturn(Optional.of("b7319b67"));

        CalendarAttendeeResolver.ResolvedAttendees result = resolver.resolve(meeting, new ObjectMapper().createObjectNode());

        assertEquals(3, result.feishuUserIds().size());
        assertTrue(result.feishuUserIds().containsAll(List.of("8813018f", "uid-tian", "b7319b67")));
    }
}

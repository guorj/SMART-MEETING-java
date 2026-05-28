package com.smartmeeting.service;

import com.smartmeeting.api.dto.MeetingCreateRequest;
import com.smartmeeting.enums.AttendanceMode;
import com.smartmeeting.enums.MeetingScenario;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 会议信息推导器：根据参会人到场方式推导三场景。
 */
@Component
public class MeetingScenarioResolver {

    /**
     * 解析最终场景：优先显式输入，其次按参会人 attendanceMode 自动推断。
     */
    public MeetingScenario resolve(String explicitScenario, List<MeetingCreateRequest.ParticipantEntry> participants) {
        if (explicitScenario != null && !explicitScenario.isBlank()) {
            return MeetingScenario.fromString(explicitScenario);
        }
        if (participants == null || participants.isEmpty()) {
            return MeetingScenario.OFFLINE;
        }
        long onlineCount = participants.stream()
                .map(MeetingCreateRequest.ParticipantEntry::getAttendanceMode)
                .map(AttendanceMode::fromString)
                .filter(mode -> mode == AttendanceMode.ONLINE)
                .count();
        if (onlineCount == 0) {
            return MeetingScenario.OFFLINE;
        }
        if (onlineCount == participants.size()) {
            return MeetingScenario.ONLINE;
        }
        return MeetingScenario.HYBRID;
    }
}

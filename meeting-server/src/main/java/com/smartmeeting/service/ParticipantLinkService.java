package com.smartmeeting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmeeting.api.dto.MeetingResponse;
import com.smartmeeting.entity.Participant;
import com.smartmeeting.enums.AttendanceMode;
import com.smartmeeting.repository.ParticipantMapper;
import com.smartmeeting.util.JwtUtil;
import com.smartmeeting.util.MeetingWebPageUrls;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 参会人个人入会链接生成服务（线上参会到场登记用）。
 * <p>
 * 主要协作组件：{@link ParticipantMapper}、{@link JwtUtil}、{@link MeetingWebPageUrls}。
 */
@Service
@RequiredArgsConstructor
public class ParticipantLinkService {

    private final ParticipantMapper participantMapper;
    private final JwtUtil jwtUtil;
    private final MeetingWebPageUrls meetingWebPageUrls;

    /**
     * 为会议所有参会人生成 DTO 列表，线上参会人附带带 JWT 的个人入会链接。
     *
     * @param meetingId 会议 ID
     * @return 参会人 DTO 列表（线上模式含 joinUrl）
     */
    public List<MeetingResponse.ParticipantDTO> buildParticipantLinks(String meetingId) {
        LambdaQueryWrapper<Participant> q = new LambdaQueryWrapper<>();
        q.eq(Participant::getMeetingId, meetingId);
        List<Participant> rows = participantMapper.selectList(q);
        List<MeetingResponse.ParticipantDTO> out = new ArrayList<>();
        for (Participant p : rows) {
            MeetingResponse.ParticipantDTO dto = new MeetingResponse.ParticipantDTO();
            dto.setUserId(p.getUserId());
            dto.setName(p.getName());
            dto.setStatus(p.getStatus());
            dto.setAttendanceMode(p.getAttendanceMode());
            dto.setCheckedInAt(p.getCheckedInAt());
            dto.setCheckInSource(p.getCheckInSource());
            if (AttendanceMode.ONLINE.name().equalsIgnoreCase(
                    p.getAttendanceMode() != null ? p.getAttendanceMode() : "")) {
                String token = jwtUtil.generateParticipantJoinToken(meetingId, p.getUserId(), p.getName());
                dto.setJoinUrl(meetingWebPageUrls.joinPageUrl(meetingId, token));
            }
            out.add(dto);
        }
        return out;
    }
}

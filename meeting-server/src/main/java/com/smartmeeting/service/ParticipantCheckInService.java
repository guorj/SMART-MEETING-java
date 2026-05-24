package com.smartmeeting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmeeting.entity.Participant;
import com.smartmeeting.enums.AttendanceMode;
import com.smartmeeting.enums.CheckInSource;
import com.smartmeeting.exception.BusinessException;
import com.smartmeeting.repository.ParticipantMapper;
import com.smartmeeting.service.host.MeetingHostSessionService;
import com.smartmeeting.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 线上参会人自动检点（到场登记）服务。
 *
 * <p>参会人通过个人入会链接 {@code /join/{meetingId}?token=…} 打开页面后，
 * 前端调用 {@code POST /api/v1/meetings/{id}/check-in}，本服务校验 JWT、更新参会人表，
 * 并同步主持端混合检点名单（{@link MeetingHostSessionService#recordOnlineCheckIn}）。
 *
 * <p>仅 {@link AttendanceMode#ONLINE} 参会人可走本链路；线下人员须在检点点名阶段现场答到。
 */
@Service
@RequiredArgsConstructor
public class ParticipantCheckInService {

    private final ParticipantMapper participantMapper;
    private final MeetingPresetTypeResolver presetTypeResolver;
    private final JwtUtil jwtUtil;
    private final MeetingHostSessionService meetingHostSessionService;

    /**
     * 根据个人入会 JWT 完成线上到场登记。
     *
     * <p>若参会人记录不存在则按令牌信息插入（默认 {@code attendanceMode=ONLINE}）；
     * 已存在则更新 {@code checked_in_at}、{@code checkInSource=AUTO_ONLINE}、{@code status=CONFIRMED}。
     *
     * @param meetingId   会议主键，须与 JWT 内 {@code meetingId} 一致
     * @param bearerToken 个人入会令牌（不含 {@code Bearer } 前缀），由 {@link JwtUtil#verifyJoinToken} 校验
     * @return 登记结果，键包括 {@code meetingId}、{@code userId}、{@code name}、
     *         {@code checkedInAt}（ISO-8601 字符串）、{@code checkInSource}
     * @throws BusinessException 令牌无效、缺少 userId、或参会人登记为线下到场（400）
     */
    @Transactional
    public Map<String, Object> checkInFromToken(String meetingId, String bearerToken) {
        JwtUtil.ParticipantMeetingToken token = jwtUtil.verifyJoinToken(bearerToken, meetingId);
        String userId = token.userId();
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(400, "入会令牌缺少用户标识");
        }
        LambdaQueryWrapper<Participant> q = new LambdaQueryWrapper<>();
        q.eq(Participant::getMeetingId, meetingId).eq(Participant::getUserId, userId);
        Participant p = participantMapper.selectOne(q);
        if (p == null) {
            p = new Participant();
            p.setId(java.util.UUID.randomUUID().toString());
            p.setMeetingId(meetingId);
            p.setPresetTypeCode(presetTypeResolver.resolve(meetingId));
            p.setUserId(userId);
            p.setName(token.displayName() != null && !token.displayName().isBlank()
                    ? token.displayName() : userId);
            p.setStatus("CONFIRMED");
            p.setAttendanceMode(AttendanceMode.ONLINE.name());
            p.setTodoCount(0);
            p.setCompletedCount(0);
            p.setVoiceprintReady(false);
            participantMapper.insert(p);
        }
        if (!AttendanceMode.ONLINE.name().equalsIgnoreCase(
                p.getAttendanceMode() != null ? p.getAttendanceMode() : AttendanceMode.ONLINE.name())) {
            throw new BusinessException(400, "该参会人登记为线下到场，请现场答到");
        }
        p.setStatus("CONFIRMED");
        p.setCheckedInAt(LocalDateTime.now());
        p.setCheckInSource(CheckInSource.AUTO_ONLINE.name());
        participantMapper.updateById(p);

        meetingHostSessionService.recordOnlineCheckIn(meetingId, p.getUserId(), p.getName());

        return Map.of(
                "meetingId", meetingId,
                "userId", p.getUserId(),
                "name", p.getName(),
                "checkedInAt", p.getCheckedInAt().toString(),
                "checkInSource", p.getCheckInSource()
        );
    }
}

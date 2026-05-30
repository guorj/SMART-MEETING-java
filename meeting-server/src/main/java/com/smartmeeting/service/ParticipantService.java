package com.smartmeeting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmeeting.entity.Participant;
import com.smartmeeting.repository.ParticipantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ParticipantService {

    private final ParticipantMapper participantMapper;

    /**
     * 更新参会确认状态。
     *
     * @return 更新成功返回 true
     */
    @Transactional
    public boolean updateConfirmStatus(String meetingId, String userId, String status) {
        if (meetingId == null || meetingId.isBlank() || userId == null || userId.isBlank()
                || status == null || status.isBlank()) {
            return false;
        }
        Participant row = participantMapper.selectOne(new LambdaQueryWrapper<Participant>()
                .eq(Participant::getMeetingId, meetingId)
                .eq(Participant::getUserId, userId)
                .last("LIMIT 1"));
        if (row == null) {
            return false;
        }
        row.setStatus(status);
        return participantMapper.updateById(row) > 0;
    }

    /**
     * 在“当前会议参会人”范围内按姓名严格映射 user_id。
     * <ul>
     *     <li>唯一命中：resolved</li>
     *     <li>0 命中：not_found</li>
     *     <li>多命中：ambiguous</li>
     * </ul>
     */
    public NameResolveResult resolveUniqueUserIdByMeetingAndName(String meetingId, String name) {
        String mid = trim(meetingId);
        String targetName = trim(name);
        if (mid == null || targetName == null) {
            return NameResolveResult.notFound();
        }
        List<Participant> rows = participantMapper.selectList(new LambdaQueryWrapper<Participant>()
                .eq(Participant::getMeetingId, mid)
                .eq(Participant::getName, targetName));
        if (rows == null || rows.isEmpty()) {
            return NameResolveResult.notFound();
        }
        String resolved = null;
        int validCount = 0;
        for (Participant p : rows) {
            String uid = trim(p.getUserId());
            if (uid == null) {
                continue;
            }
            validCount++;
            if (resolved == null) {
                resolved = uid;
            } else if (!resolved.equals(uid)) {
                return NameResolveResult.ambiguous();
            }
        }
        if (validCount == 1 && resolved != null) {
            return NameResolveResult.resolved(resolved);
        }
        if (validCount > 1) {
            return NameResolveResult.ambiguous();
        }
        return NameResolveResult.notFound();
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        String out = value.trim();
        return out.isEmpty() ? null : out;
    }

    public record NameResolveResult(String status, String userId) {
        public static NameResolveResult resolved(String userId) {
            return new NameResolveResult("resolved", userId);
        }

        public static NameResolveResult notFound() {
            return new NameResolveResult("not_found", null);
        }

        public static NameResolveResult ambiguous() {
            return new NameResolveResult("ambiguous", null);
        }
    }
}


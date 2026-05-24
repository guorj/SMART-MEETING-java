package com.smartmeeting.service;

import com.smartmeeting.entity.Meeting;
import com.smartmeeting.repository.MeetingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 按会议 ID 解析 {@code preset_type_code}（进程内短缓存，减轻转写高频写入时的查询）。
 */
@Service
@RequiredArgsConstructor
public class MeetingPresetTypeResolver {

    private final MeetingMapper meetingMapper;
    private final ConcurrentHashMap<String, Integer> cache = new ConcurrentHashMap<>();

    public Integer resolve(String meetingId) {
        if (meetingId == null || meetingId.isBlank()) {
            return null;
        }
        String id = meetingId.trim();
        return cache.computeIfAbsent(id, this::loadFromDb);
    }

    public void remember(String meetingId, Integer presetTypeCode) {
        if (meetingId == null || meetingId.isBlank()) {
            return;
        }
        String id = meetingId.trim();
        // ConcurrentHashMap 不允许 null 值；无预设编码时不缓存
        if (presetTypeCode == null) {
            cache.remove(id);
            return;
        }
        cache.put(id, presetTypeCode);
    }

    public void evict(String meetingId) {
        if (meetingId != null && !meetingId.isBlank()) {
            cache.remove(meetingId.trim());
        }
    }

    private Integer loadFromDb(String meetingId) {
        Meeting meeting = meetingMapper.selectById(meetingId);
        return meeting != null ? meeting.getPresetTypeCode() : null;
    }
}

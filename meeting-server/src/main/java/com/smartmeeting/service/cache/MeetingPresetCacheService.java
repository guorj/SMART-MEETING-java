package com.smartmeeting.service.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.entity.MeetingTypePreset;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 会务类型预设 Redis 缓存（不可用时回退进程内 Map）。
 * <p>
 * 键前缀：{@code smart-meeting:preset:}
 */
@Slf4j
@Service
public class MeetingPresetCacheService {

    private static final String PRESET_KEY = "smart-meeting:preset:";

    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    private final ConcurrentHashMap<Integer, MeetingTypePreset> localPreset = new ConcurrentHashMap<>();

    @Value("${meeting.cache.preset-ttl-hours:24}")
    private long presetTtlHours;

    public MeetingPresetCacheService(
            ObjectMapper objectMapper,
            @Autowired(required = false) RedisTemplate<String, Object> redisTemplate) {
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        if (redisTemplate != null) {
            log.info("MeetingPresetCacheService: Redis 缓存已启用，preset TTL={}h", presetTtlHours);
        } else {
            log.info("MeetingPresetCacheService: Redis 未注入，使用进程内缓存");
        }
    }

    public MeetingTypePreset getPreset(int presetTypeCode, Supplier<MeetingTypePreset> loader) {
        if (presetTypeCode <= 0 || loader == null) {
            return null;
        }
        String key = PRESET_KEY + presetTypeCode;
        if (redisTemplate != null) {
            try {
                Object raw = redisTemplate.opsForValue().get(key);
                if (raw instanceof String json && !json.isBlank()) {
                    return objectMapper.readValue(json, MeetingTypePreset.class);
                }
            } catch (Exception e) {
                log.warn("Redis get preset code={} failed: {}", presetTypeCode, e.getMessage());
            }
        } else {
            MeetingTypePreset cached = localPreset.get(presetTypeCode);
            if (cached != null) {
                return cached;
            }
        }
        MeetingTypePreset loaded = loader.get();
        if (loaded == null) {
            return null;
        }
        putPreset(presetTypeCode, loaded);
        return loaded;
    }

    public PresetBundle putPresetBundle(int presetTypeCode, MeetingTypePreset preset) {
        if (presetTypeCode <= 0) {
            return new PresetBundle(null);
        }
        if (preset != null) {
            putPreset(presetTypeCode, preset);
        }
        return new PresetBundle(preset);
    }

    public void evictPreset(int presetTypeCode) {
        if (presetTypeCode <= 0) {
            return;
        }
        localPreset.remove(presetTypeCode);
        if (redisTemplate != null) {
            try {
                redisTemplate.delete(PRESET_KEY + presetTypeCode);
            } catch (Exception e) {
                log.warn("Redis evict preset code={} failed: {}", presetTypeCode, e.getMessage());
            }
        }
    }

    private void putPreset(int presetTypeCode, MeetingTypePreset preset) {
        if (redisTemplate != null) {
            try {
                String json = objectMapper.writeValueAsString(preset);
                redisTemplate.opsForValue().set(PRESET_KEY + presetTypeCode, json, Duration.ofHours(presetTtlHours));
                return;
            } catch (Exception e) {
                log.warn("Redis put preset code={} failed: {}", presetTypeCode, e.getMessage());
            }
        }
        localPreset.put(presetTypeCode, preset);
    }
}

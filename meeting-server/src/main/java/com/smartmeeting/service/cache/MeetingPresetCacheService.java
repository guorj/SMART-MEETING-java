package com.smartmeeting.service.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.entity.MatterProgressDocConfig;
import com.smartmeeting.entity.MeetingTypePreset;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 会务类型预设与飞书资料配置的 Redis 缓存（不可用时回退进程内 Map）。
 * <p>
 * 键前缀：{@code smart-meeting:preset:}、{@code smart-meeting:matter-progress-doc:preset:}
 */
@Slf4j
@Service
public class MeetingPresetCacheService {

    private static final String PRESET_KEY = "smart-meeting:preset:";
    private static final String MATTER_DOC_KEY = "smart-meeting:matter-progress-doc:preset:";

    private static final TypeReference<List<MatterProgressDocConfig>> MATTER_DOC_LIST_TYPE =
            new TypeReference<>() {};

    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    private final ConcurrentHashMap<Integer, MeetingTypePreset> localPreset = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, List<MatterProgressDocConfig>> localMatterDocs =
            new ConcurrentHashMap<>();

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

    /**
     * 按 preset code 获取会务类型预设（先 Redis，未命中再 loader 并回填）。
     */
    public MeetingTypePreset getPreset(int presetTypeCode, Supplier<MeetingTypePreset> loader) {
        if (presetTypeCode < 1 || presetTypeCode > 5 || loader == null) {
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

    /**
     * 按 preset code 获取已启用的会序飞书资料配置列表。
     */
    public List<MatterProgressDocConfig> getMatterProgressDocs(int presetTypeCode,
                                                             Supplier<List<MatterProgressDocConfig>> loader) {
        if (presetTypeCode < 1 || presetTypeCode > 5 || loader == null) {
            return List.of();
        }
        String key = MATTER_DOC_KEY + presetTypeCode;
        if (redisTemplate != null) {
            try {
                Object raw = redisTemplate.opsForValue().get(key);
                if (raw instanceof String json && !json.isBlank()) {
                    List<MatterProgressDocConfig> list = objectMapper.readValue(json, MATTER_DOC_LIST_TYPE);
                    return list != null ? list : List.of();
                }
            } catch (Exception e) {
                log.warn("Redis get matter-doc preset={} failed: {}", presetTypeCode, e.getMessage());
            }
        } else {
            List<MatterProgressDocConfig> cached = localMatterDocs.get(presetTypeCode);
            if (cached != null) {
                return cached;
            }
        }
        List<MatterProgressDocConfig> loaded = loader.get();
        if (loaded == null) {
            loaded = List.of();
        }
        putMatterProgressDocs(presetTypeCode, loaded);
        return loaded;
    }

    /**
     * 强制将 DB 查询结果写入缓存（不读旧 Redis），用于创建会议与定时预热。
     */
    public PresetBundle putPresetBundle(int presetTypeCode, MeetingTypePreset preset,
                                        List<MatterProgressDocConfig> matterDocs) {
        if (presetTypeCode < 1 || presetTypeCode > 5) {
            return new PresetBundle(null, List.of());
        }
        if (preset != null) {
            putPreset(presetTypeCode, preset);
        }
        List<MatterProgressDocConfig> docs = matterDocs != null ? matterDocs : List.of();
        putMatterProgressDocs(presetTypeCode, docs);
        return new PresetBundle(preset, docs);
    }

    /** 运维更新预设或资料配置后可调用，清除指定 code 的缓存。 */
    public void evictPreset(int presetTypeCode) {
        if (presetTypeCode < 1 || presetTypeCode > 5) {
            return;
        }
        localPreset.remove(presetTypeCode);
        localMatterDocs.remove(presetTypeCode);
        if (redisTemplate != null) {
            try {
                redisTemplate.delete(PRESET_KEY + presetTypeCode);
                redisTemplate.delete(MATTER_DOC_KEY + presetTypeCode);
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

    private void putMatterProgressDocs(int presetTypeCode, List<MatterProgressDocConfig> list) {
        if (redisTemplate != null) {
            try {
                String json = objectMapper.writeValueAsString(list);
                redisTemplate.opsForValue().set(MATTER_DOC_KEY + presetTypeCode, json, Duration.ofHours(presetTtlHours));
                return;
            } catch (Exception e) {
                log.warn("Redis put matter-doc preset={} failed: {}", presetTypeCode, e.getMessage());
            }
        }
        localMatterDocs.put(presetTypeCode, List.copyOf(list));
    }
}

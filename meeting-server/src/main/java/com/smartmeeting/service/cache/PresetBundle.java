package com.smartmeeting.service.cache;

import com.smartmeeting.entity.MatterProgressDocConfig;
import com.smartmeeting.entity.MeetingTypePreset;

import java.util.List;

/**
 * 会务预设与飞书资料配置的 DB 快照（写入 Redis 前的载体）。
 */
public record PresetBundle(MeetingTypePreset preset, List<MatterProgressDocConfig> matterDocs) {
}

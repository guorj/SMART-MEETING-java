package com.smartmeeting.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartmeeting.entity.MeetingTypePreset;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会务类型预设 Mapper，对应实体 {@link MeetingTypePreset} / 数据库表 {@code int_meeting_type_preset}。
 */
@Mapper
public interface MeetingTypePresetMapper extends BaseMapper<MeetingTypePreset> {
}

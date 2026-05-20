package com.smartmeeting.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartmeeting.entity.MeetingMinute;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会议纪要 Mapper，对应实体 {@link MeetingMinute} / 数据库表 {@code int_meeting_minute}。
 */
@Mapper
public interface MeetingMinuteMapper extends BaseMapper<MeetingMinute> {
}

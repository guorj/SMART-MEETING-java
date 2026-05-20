package com.smartmeeting.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartmeeting.entity.Meeting;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会议主表 Mapper，对应实体 {@link Meeting} / 数据库表 {@code int_meeting}。
 */
@Mapper
public interface MeetingMapper extends BaseMapper<Meeting> {
}

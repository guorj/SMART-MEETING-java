package com.smartmeeting.admin.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartmeeting.admin.entity.MeetingMinute;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MeetingMinuteMapper extends BaseMapper<MeetingMinute> {
}

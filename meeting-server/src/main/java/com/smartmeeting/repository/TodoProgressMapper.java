package com.smartmeeting.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartmeeting.entity.MeetingTodoProgress;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TodoProgressMapper extends BaseMapper<MeetingTodoProgress> {
}

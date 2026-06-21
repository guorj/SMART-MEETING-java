package com.smartmeeting.admin.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartmeeting.admin.entity.MeetingTodo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MeetingTodoMapper extends BaseMapper<MeetingTodo> {
}

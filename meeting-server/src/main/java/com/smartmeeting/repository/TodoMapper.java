package com.smartmeeting.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartmeeting.entity.MeetingTodo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会议待办 Mapper，对应实体 {@link MeetingTodo} / 数据库表 {@code int_meeting_todo}。
 */
@Mapper
public interface TodoMapper extends BaseMapper<MeetingTodo> {
}

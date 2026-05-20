package com.smartmeeting.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartmeeting.entity.Participant;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会议参会人 Mapper，对应实体 {@link Participant} / 数据库表 {@code int_meeting_participant}。
 */
@Mapper
public interface ParticipantMapper extends BaseMapper<Participant> {
}

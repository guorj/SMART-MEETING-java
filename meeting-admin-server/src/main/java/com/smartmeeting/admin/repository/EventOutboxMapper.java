package com.smartmeeting.admin.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartmeeting.admin.entity.EventOutbox;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EventOutboxMapper extends BaseMapper<EventOutbox> {
}

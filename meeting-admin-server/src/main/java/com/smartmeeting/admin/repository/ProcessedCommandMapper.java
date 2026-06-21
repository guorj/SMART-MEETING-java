package com.smartmeeting.admin.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartmeeting.admin.entity.ProcessedCommand;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProcessedCommandMapper extends BaseMapper<ProcessedCommand> {
}

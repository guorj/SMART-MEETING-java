package com.smartmeeting.admin.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartmeeting.admin.entity.PipelineStepExecution;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PipelineStepExecutionMapper extends BaseMapper<PipelineStepExecution> {
}

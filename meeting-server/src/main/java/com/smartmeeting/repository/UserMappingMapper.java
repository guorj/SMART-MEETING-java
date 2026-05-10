package com.smartmeeting.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartmeeting.entity.UserMapping;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户映射 Mapper - OA用户 ↔ 飞书ID
 */
@Mapper
public interface UserMappingMapper extends BaseMapper<UserMapping> {
}
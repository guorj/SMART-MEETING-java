package com.smartmeeting.admin.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartmeeting.admin.entity.UserMapping;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMappingMapper extends BaseMapper<UserMapping> {
}

package com.smartmeeting.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartmeeting.entity.UserMapping;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户映射 Mapper，对应实体 {@link UserMapping} / 数据库表 {@code int_user_mapping_feishu}。
 * <p>
 * 维护 OA 用户与飞书 ID 的对应关系，用于待办责任人匹配与消息推送。
 */
@Mapper
public interface UserMappingMapper extends BaseMapper<UserMapping> {
}

package com.smartmeeting.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartmeeting.entity.MatterProgressDocConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会序飞书资料配置 Mapper，对应实体 {@link MatterProgressDocConfig} / 数据库表 {@code int_matter_progress_doc_config}。
 */
@Mapper
public interface MatterProgressDocConfigMapper extends BaseMapper<MatterProgressDocConfig> {
}

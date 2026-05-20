package com.smartmeeting.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartmeeting.entity.Voiceprint;
import org.apache.ibatis.annotations.Mapper;

/**
 * 声纹注册 Mapper，对应实体 {@link Voiceprint} / 数据库表 {@code int_voiceprint}。
 */
@Mapper
public interface VoiceprintMapper extends BaseMapper<Voiceprint> {
}

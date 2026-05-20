package com.smartmeeting.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartmeeting.entity.TranscriptSegment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 实时转写片段 Mapper，对应实体 {@link TranscriptSegment} / 数据库表 {@code int_transcript_segment}。
 */
@Mapper
public interface TranscriptMapper extends BaseMapper<TranscriptSegment> {
}

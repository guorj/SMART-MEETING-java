package com.smartmeeting.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartmeeting.entity.TranscriptSegment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TranscriptMapper extends BaseMapper<TranscriptSegment> {
}

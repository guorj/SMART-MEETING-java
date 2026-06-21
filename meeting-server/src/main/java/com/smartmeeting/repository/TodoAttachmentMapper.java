package com.smartmeeting.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartmeeting.entity.MeetingTodoAttachment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TodoAttachmentMapper extends BaseMapper<MeetingTodoAttachment> {
}

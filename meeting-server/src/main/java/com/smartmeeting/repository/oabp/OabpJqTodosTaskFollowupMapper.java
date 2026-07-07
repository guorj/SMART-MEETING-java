package com.smartmeeting.repository.oabp;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartmeeting.entity.oabp.OabpJqTodosTaskFollowup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * oabp 库 {@code jq_todos_task_followup} Mapper，绑定 {@code oabpSqlSessionFactory}。
 * <p>
 * 简单 CRUD 走 {@link BaseMapper}；按任务 ID 查最新跟进等场景走 XML。
 * </p>
 */
@Mapper
public interface OabpJqTodosTaskFollowupMapper extends BaseMapper<OabpJqTodosTaskFollowup> {

    /**
     * 查询指定任务的最新一条跟进记录（按 id 降序取首条）。
     *
     * @param taskId   任务 ID
     * @param taskType 任务类型；0=任务主跟进
     * @return 最新跟进记录；无匹配返回 null
     */
    OabpJqTodosTaskFollowup findLatestByTaskId(@Param("taskId") Long taskId,
                                                @Param("taskType") Integer taskType);

    /**
     * 查询指定任务的全部跟进记录（按 id 降序）。
     *
     * @param taskId   任务 ID
     * @param taskType 任务类型；null 表示不限
     * @return 跟进记录列表；无匹配返回空列表
     */
    List<OabpJqTodosTaskFollowup> findByTaskId(@Param("taskId") Long taskId,
                                                @Param("taskType") Integer taskType);
}

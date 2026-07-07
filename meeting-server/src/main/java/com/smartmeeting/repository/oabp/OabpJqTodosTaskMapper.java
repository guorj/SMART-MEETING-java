package com.smartmeeting.repository.oabp;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartmeeting.entity.oabp.OabpJqTodosTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * oabp 库 {@code jq_todos_task} Mapper，绑定 {@code oabpSqlSessionFactory}。
 * <p>
 * 简单 CRUD 走 {@link BaseMapper}；按飞书 recId 查询、批量按状态查询等复杂场景走 XML。
 * </p>
 */
@Mapper
public interface OabpJqTodosTaskMapper extends BaseMapper<OabpJqTodosTask> {

    /**
     * 按飞书 recId 查找任务（remark 字段格式 {@code [feishu:recId=xxx]...}）。
     *
     * @param feishuRecId 飞书记录 ID
     * @return 匹配的任务；无匹配返回 null
     */
    OabpJqTodosTask findByFeishuRecId(@Param("feishuRecId") String feishuRecId);

    /**
     * 按状态查询未软删的任务列表。
     *
     * @param status 状态：0=未开始，1=进行中，2=已完成，3=已延期
     * @return 匹配任务列表；无匹配返回空列表
     */
    List<OabpJqTodosTask> findByStatus(@Param("status") Integer status);

    /**
     * 按 remark 前缀查询任务（用于飞书导入任务的批量统计/校验）。
     *
     * @param remarkPrefix remark 前缀，如 {@code [feishu:recId=%}
     * @return 匹配任务列表
     */
    List<OabpJqTodosTask> findByRemarkPrefix(@Param("remarkPrefix") String remarkPrefix);

    /**
     * 软删除任务（deleted=1）。
     *
     * @param id 任务 ID
     * @param updater 更新人
     * @return 受影响行数
     */
    int softDelete(@Param("id") Long id, @Param("updater") String updater);
}

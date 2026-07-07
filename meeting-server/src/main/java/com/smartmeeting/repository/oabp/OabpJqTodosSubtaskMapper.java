package com.smartmeeting.repository.oabp;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartmeeting.entity.oabp.OabpJqTodosSubtask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * oabp 库 {@code jq_todos_subtask} Mapper，绑定 {@code oabpSqlSessionFactory}。
 * <p>
 * 简单 CRUD 走 {@link BaseMapper}；按父任务/飞书 recId 查询等场景走 XML。
 * </p>
 */
@Mapper
public interface OabpJqTodosSubtaskMapper extends BaseMapper<OabpJqTodosSubtask> {

    /**
     * 按父任务 ID 查询未软删的子任务。
     *
     * @param parentId 父任务 ID
     * @return 子任务列表；无匹配返回空列表
     */
    List<OabpJqTodosSubtask> findActiveByParentId(@Param("parentId") Long parentId);

    /**
     * 按飞书 recId 查询所有关联子任务（remark 格式 {@code [feishu:recId=xxx:sub=N]}）。
     *
     * @param feishuRecId 飞书记录 ID
     * @return 子任务列表
     */
    List<OabpJqTodosSubtask> findByFeishuRecId(@Param("feishuRecId") String feishuRecId);

    /**
     * 软删除指定父任务下超出保留数量的多余子任务。
     *
     * @param parentId   父任务 ID
     * @param keepCount  保留下标 0..keepCount-1（按 id 升序）
     * @param updater    更新人
     * @return 软删条数
     */
    int softDeleteExtraByParent(@Param("parentId") Long parentId,
                                @Param("keepCount") int keepCount,
                                @Param("updater") String updater);

    /**
     * 按父任务 ID + 执行人 OABP 用户 ID 查找未软删的子任务（幂等查找核心）。
     *
     * @param parentId      父任务 ID（jq_todos_task.id）
     * @param asigneeOaId   执行人 OABP 系统用户 ID（system_users.id）
     * @return 匹配的子任务；无匹配返回 null
     */
    OabpJqTodosSubtask findByParentIdAndAsigneeId(@Param("parentId") Long parentId,
                                                  @Param("asigneeOaId") Integer asigneeOaId);
}

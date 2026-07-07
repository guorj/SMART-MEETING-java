package com.smartmeeting.entity.oabp;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * oabp 库 {@code jq_todos_subtask} 表实体（任务子表/执行人拆分）。
 * <p>
 * 字段对齐 oabp 库实际 DDL：{@code id/parent_id/asignee_id/creator/updater} 均为 {@code int}，
 * {@code deleted int}。一条 {@link OabpJqTodosTask} 可拆分多条 subtask，每条对应一位执行人。
 * </p>
 * <p>
 * 注意：执行人字段名为 {@code asignee_id}（oabp 原始拼写，非 assignee），实体沿用以免迁移时混淆。
 * </p>
 */
@Data
@TableName("jq_todos_subtask")
public class OabpJqTodosSubtask {

    /** 主键（自增 int） */
    @TableId(type = IdType.AUTO)
    private Integer id;

    /** 父任务 ID（int，NOT NULL，关联 {@link OabpJqTodosTask#getId()}） */
    private Integer parentId;

    /** 子任务名称（varchar(255)，可空） */
    private String taskName;

    /** 执行人用户 ID（int，NOT NULL，oabp 原始拼写 asignee_id）；0 表示未指派 */
    private Integer asigneeId;

    /** 备注（text，可空）；飞书导入子任务格式为 {@code [feishu:recId=xxx:sub=N]} */
    private String remark;

    /** 创建者（int，可空） */
    private Integer creator;

    /** 更新者（int，可空） */
    private Integer updater;

    /** 创建时间（datetime，可空） */
    private LocalDateTime createTime;

    /** 更新时间（datetime，可空） */
    private LocalDateTime updateTime;

    /** 是否删除（int，NOT NULL，默认 0）；0=正常，1=已删除 */
    private Integer deleted;

    /** 租户编号（int，NOT NULL，默认 0） */
    private Integer tenantId;
}

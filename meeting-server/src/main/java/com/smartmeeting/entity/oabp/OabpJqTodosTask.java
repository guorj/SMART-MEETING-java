package com.smartmeeting.entity.oabp;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * oabp 库 {@code jq_todos_task} 表实体（待办任务主表）。
 * <p>
 * 字段对齐 oabp 库实际 DDL（{@code deleted bit(1)}、{@code tenant_id bigint}）。
 * </p>
 * <p>
 * 状态约定（{@link #status}）：0=未开始，1=进行中，2=已完成，3=已延期。
 * </p>
 *
 * @see OabpJqTodosSubtask
 * @see OabpJqTodosTaskFollowup
 */
@Data
@TableName("jq_todos_task")
public class OabpJqTodosTask {

    /** 主键（自增 bigint） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务名称（NOT NULL） */
    private String taskName;

    /** 所属板块/业务单元（NOT NULL） */
    private String businessBlock;

    /** 所属项目 ID（关联 jq_project.id，可空） */
    private Long projectId;

    /** 决策人系统用户 ID（NOT NULL，关联 system_users.id，只有决策人能关闭任务） */
    private Long decisionMakerUserId;

    /** 当前任务进度 0-100（NOT NULL，默认 0） */
    private Integer progress;

    /** 任务开始日期（NOT NULL） */
    private LocalDate startDate;

    /** 计划结束日期（NOT NULL） */
    private LocalDate plannedEndDate;

    /** 任务状态（NOT NULL）：0=未开始，1=进行中，2=已完成，3=已延期 */
    private Integer status;

    /** 备注（text，可空）；飞书导入任务格式为 {@code [feishu:recId=xxx]} */
    private String remark;

    /** 任务详情（text，可空，长期任务说明） */
    private String taskDetail;

    /** 创建者（varchar(64)，默认空串） */
    private String creator;

    /** 创建时间（NOT NULL，默认 CURRENT_TIMESTAMP） */
    private LocalDateTime createTime;

    /** 更新者（varchar(64)，默认空串） */
    private String updater;

    /** 更新时间（NOT NULL，默认 CURRENT_TIMESTAMP ON UPDATE） */
    private LocalDateTime updateTime;

    /** 是否删除（bit(1)，NOT NULL，默认 b'0'）；false=正常，true=已删除 */
    private Boolean deleted;

    /** 租户编号（bigint，NOT NULL，默认 0） */
    private Long tenantId;
}

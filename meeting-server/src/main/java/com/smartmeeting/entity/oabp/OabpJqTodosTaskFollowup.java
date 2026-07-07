package com.smartmeeting.entity.oabp;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * oabp 库 {@code jq_todos_task_followup} 表实体（任务跟进明细）。
 * <p>
 * 字段对齐 oabp 库实际 DDL。注意该表<b>没有 progress/status 字段</b>（与早期反推不同），
 * 进度信息记录在 {@link #followupContent} / {@link #lastWeekProgress} / {@link #thisWeekPlan} 文本字段中。
 * </p>
 * <p>
 * {@link #taskType}：0=主任务跟进，1=子任务跟进（DDL 默认 1）。
 * {@link #reportDate} 为 NOT NULL 必填，写入时必须提供。
 * </p>
 * <p>
 * 会序查询取 {@code task_type=0} 的最新一条（MAX(id)）。
 * </p>
 */
@Data
@TableName("jq_todos_task_followup")
public class OabpJqTodosTaskFollowup {

    /** 主键（自增 bigint） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务 ID（bigint，NOT NULL，关联 jq_todos_task_tracking.id；早期 SQL 注释如此，实际也关联 jq_todos_task.id） */
    private Long taskId;

    /** 任务类型（int，默认 1）：0=主任务跟进，1=子任务跟进 */
    private Integer taskType;

    /** 任务跟进记录（text，可空） */
    private String followupContent;

    /** 上周进度（text，可空） */
    private String lastWeekProgress;

    /** 本周计划（text，可空） */
    private String thisWeekPlan;

    /** 汇报日期（date，NOT NULL 必填） */
    private LocalDate reportDate;

    /** 创建者（varchar(64)，默认空串） */
    private String creator;

    /** 创建时间（datetime，NOT NULL，默认 CURRENT_TIMESTAMP） */
    private LocalDateTime createTime;

    /** 更新者（varchar(64)，默认空串） */
    private String updater;

    /** 更新时间（datetime，NOT NULL，默认 CURRENT_TIMESTAMP ON UPDATE） */
    private LocalDateTime updateTime;

    /** 是否删除（bit(1)，NOT NULL，默认 b'0'）；false=正常，true=已删除 */
    private Boolean deleted;

    /** 租户编号（bigint，NOT NULL，默认 0） */
    private Long tenantId;
}

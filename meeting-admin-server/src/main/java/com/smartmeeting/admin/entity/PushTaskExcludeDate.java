package com.smartmeeting.admin.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

@Data
@TableName("int_scheduled_task_exclude_date")
public class PushTaskExcludeDate {
    @TableId
    private String id;
    private String taskId;
    private LocalDate excludeDate;
}

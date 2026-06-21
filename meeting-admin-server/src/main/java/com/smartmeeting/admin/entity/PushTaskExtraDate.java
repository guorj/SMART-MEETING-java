package com.smartmeeting.admin.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

@Data
@TableName("int_scheduled_task_extra_date")
public class PushTaskExtraDate {
    @TableId
    private String id;
    private String taskId;
    private LocalDate extraDate;
}

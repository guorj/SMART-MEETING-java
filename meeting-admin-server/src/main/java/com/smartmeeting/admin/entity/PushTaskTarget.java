package com.smartmeeting.admin.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("int_scheduled_push_task_target")
public class PushTaskTarget {
    @TableId
    private String id;
    private String taskId;
    private String targetType;
    private String targetId;
    private Integer sortOrder;
}

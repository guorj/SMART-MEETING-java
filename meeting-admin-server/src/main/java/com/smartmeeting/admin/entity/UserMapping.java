package com.smartmeeting.admin.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * OA 用户与飞书 user_id 映射，对应 {@code int_user_mapping_feishu}。
 */
@Data
@TableName("int_user_mapping_feishu")
public class UserMapping {

    @TableId
    private Integer userId;

    private String userName;

    private String feishuUserId;

    /** 直属上级飞书 user_id（用于待办延期升级提醒） */
    private String supervisorFeishuUserId;
}

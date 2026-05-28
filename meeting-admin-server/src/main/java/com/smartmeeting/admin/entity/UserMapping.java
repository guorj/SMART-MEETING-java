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
}

package com.smartmeeting.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 用户映射实体 - OA用户 ↔ 飞书ID
 *
 * 用于待办责任人匹配：
 * - OA系统 userId → 飞书 user_id/open_id/union_id
 */
@Data
@TableName("int_user_mapping")
public class UserMapping {

    /** OA用户ID（主键） */
    @TableId
    private Integer userId;

    /** 用户姓名 */
    private String userName;

    /** 飞书user_id */
    private String feishuUserId;

    /** 飞书union_id */
    private String feishuUnionId;

    /** 飞书open_id */
    private String feishuOpenId;
}
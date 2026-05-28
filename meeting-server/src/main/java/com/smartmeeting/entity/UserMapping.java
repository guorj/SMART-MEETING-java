package com.smartmeeting.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 用户映射实体，对应数据库表 {@code int_user_mapping_feishu}。
 * <p>
 * 维护 OA 用户与飞书 {@code user_id} 的对应关系，用于待办责任人匹配与消息推送。
 */
@Data
@TableName("int_user_mapping_feishu")
public class UserMapping {

    /** OA 用户 ID（主键） */
    @TableId
    private Integer userId;

    /** 用户姓名 */
    private String userName;

    /** 飞书 user_id（企业内唯一） */
    private String feishuUserId;
}

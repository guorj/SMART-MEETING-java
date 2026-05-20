package com.smartmeeting.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 声纹注册实体，对应数据库表 {@code int_voiceprint}。
 * <p>
 * 存储参会人在讯飞平台的声纹特征，用于 ASR 说话人分离与身份匹配。
 */
@Data
@TableName("int_voiceprint")
public class Voiceprint {
    /** 声纹记录唯一标识（主键） */
    @TableId
    private String id;
    /** OA 用户 ID，关联 {@code int_user_mapping.user_id} */
    private Integer userId;
    /** 用户姓名 */
    private String userName;
    /** 飞书 user_id */
    private String feishuUserId;
    /** 讯飞声纹特征 ID */
    private String featureId;
    /** 讯飞声纹分组 ID */
    private String groupId;
    /** 声纹注册时间 */
    private LocalDateTime registeredAt;
    /** 声纹过期时间（讯飞平台有效期） */
    private LocalDateTime expiresAt;
}

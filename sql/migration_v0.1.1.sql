-- ============================================================
-- 智能会议系统 - 增量迁移脚本
-- 版本: v0.1.1
-- 日期: 2026-05-09
-- 说明: 基于联调测试反馈的修复项
-- ============================================================

-- 1. 添加 chat_id 列（用于飞书群聊消息推送）
ALTER TABLE int_meeting 
ADD COLUMN chat_id VARCHAR(100) NULL COMMENT '飞书群聊ID(用于消息推送)' 
AFTER creator_id;

-- 2. 添加索引（可选，如果查询需要）
-- ALTER TABLE int_meeting ADD INDEX idx_meeting_chat (chat_id);
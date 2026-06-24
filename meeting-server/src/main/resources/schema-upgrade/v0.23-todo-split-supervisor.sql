-- =============================================================================
-- v0.23 待办拆分(parent_id) + 用户直属上级(supervisor_id)
-- =============================================================================
-- 1. int_meeting_todo 增加 parent_id：支持待办拆分为子待办
-- 2. int_user_mapping_feishu 增加 supervisor_feishu_user_id：直属上级飞书user_id，用于延期升级提醒
-- 说明：全部 ALTER + ADD IF NOT EXISTS 语义（MySQL 无 ADD IF NOT EXISTS，用存储过程兼容）
-- =============================================================================

-- int_meeting_todo.parent_id
ALTER TABLE int_meeting_todo
    ADD COLUMN parent_id VARCHAR(36) NULL COMMENT '父待办ID（拆分来源）' AFTER next_meeting_id,
    ADD INDEX idx_todo_parent (parent_id);

-- int_user_mapping_feishu.supervisor_feishu_user_id
ALTER TABLE int_user_mapping_feishu
    ADD COLUMN supervisor_feishu_user_id VARCHAR(100) NULL COMMENT '直属上级飞书user_id' AFTER feishu_user_id,
    ADD INDEX idx_user_mapping_supervisor (supervisor_feishu_user_id);

-- =============================================================================
-- v0.25 飞书 VC 云端录制接入：int_meeting 新增妙记关联字段
-- =============================================================================
-- 用途：
--   vc_meeting_url   飞书 VC 入会链接（日历 vchat.meeting_url，建会时持久化）
--   vc_minute_token  妙记 token（从 recording_ready_v1 回调 event.url 后缀提取，24 字符）
--   vc_recording_url 妙记页面 URL（与回调 event.url 一致，便于人工核对）
-- 幂等：先检查列是否存在再 ADD，可重复执行。
-- =============================================================================

-- MySQL 8+ 用 INFORMATION_SCHEMA 判断列是否存在后幂等 ADD
SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'int_meeting'
      AND COLUMN_NAME = 'vc_meeting_url'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE int_meeting ADD COLUMN vc_meeting_url VARCHAR(512) NULL COMMENT ''飞书VC入会链接（日历vchat.meeting_url）''',
    'SELECT ''vc_meeting_url already exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'int_meeting'
      AND COLUMN_NAME = 'vc_minute_token'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE int_meeting ADD COLUMN vc_minute_token VARCHAR(64) NULL COMMENT ''妙记token（recording_ready回调提取）''',
    'SELECT ''vc_minute_token already exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'int_meeting'
      AND COLUMN_NAME = 'vc_recording_url'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE int_meeting ADD COLUMN vc_recording_url VARCHAR(512) NULL COMMENT ''妙记页面URL（回调event.url）''',
    'SELECT ''vc_recording_url already exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

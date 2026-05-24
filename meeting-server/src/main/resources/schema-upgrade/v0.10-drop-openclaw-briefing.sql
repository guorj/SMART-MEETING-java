-- =============================================================================
-- v0.10：移除 int_matter_progress_doc_config.openclaw_briefing（legacy 会中 OpenClaw 通报开关）
--
-- 替代方案（v0.9+）：
--   - feishu-scheduled-bot 定时 Job → 写回 generated_report_url
--   - config_role = SOURCE | OUTPUT | BOTH
--
-- 阶段 0：先将非 0 行置 0（幂等），再 DROP 列。
-- 执行前建议：SELECT config_name, openclaw_briefing FROM int_matter_progress_doc_config WHERE openclaw_briefing <> 0;
-- =============================================================================

UPDATE int_matter_progress_doc_config
SET openclaw_briefing = 0
WHERE openclaw_briefing IS NOT NULL AND openclaw_briefing <> 0;

SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'int_matter_progress_doc_config'
      AND column_name = 'openclaw_briefing'
);

SET @drop_sql := IF(@col_exists > 0,
    'ALTER TABLE int_matter_progress_doc_config DROP COLUMN openclaw_briefing',
    'SELECT ''openclaw_briefing already dropped'' AS _skip');

PREPARE stmt FROM @drop_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 可选运维脚本：为 int_matter_progress_doc_config 增加 OpenClaw 会序通报开关列（勿随应用自动执行）
-- v0.6 legacy：openclaw_briefing 列（会中 OpenClaw 通报）
-- ⚠️ 已废弃：v0.9 起改 feishu-scheduled-bot + generated_report_url；v0.10 执行 schema-upgrade/v0.10-drop-openclaw-briefing.sql 删除列
-- 新部署：勿执行本脚本 ADD 列；直接使用 schema.sql（无 openclaw_briefing）
-- 文档：docs/weekly-matter-comparison.md
--       WHERE preset_type_code=1 AND agenda_index=1 AND config_name='preset1-comp-agenda-01';

SET @col_ob := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'int_matter_progress_doc_config'
      AND column_name = 'openclaw_briefing'
);
SET @sql_ob := IF(@col_ob = 0,
    'ALTER TABLE int_matter_progress_doc_config ADD COLUMN openclaw_briefing TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''1=该会序进入 RUNNING 时触发 OpenClaw 通报'' AFTER feishu_doc_url',
    'SELECT ''openclaw_briefing exists'' AS _skip');
PREPARE stmt_ob FROM @sql_ob;
EXECUTE stmt_ob;
DEALLOCATE PREPARE stmt_ob;

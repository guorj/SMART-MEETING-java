-- 可选运维脚本：为 int_matter_progress_doc_config 增加 OpenClaw 会序通报开关列（勿随应用自动执行）
-- 默认 0：仅补飞书 URL 不触发通报；需通报时请 UPDATE openclaw_briefing=1
-- 仅当 openclaw_briefing=1 且同行 feishu_doc_url 有效时调用 OpenClaw（其余情况不调用）
-- 示例：UPDATE int_matter_progress_doc_config SET openclaw_briefing=1
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

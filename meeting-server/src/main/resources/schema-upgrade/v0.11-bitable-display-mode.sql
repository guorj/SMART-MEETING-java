-- =============================================================================
-- v0.11 会序多维表格展示模式 — int_matter_progress_doc_config.bitable_display_mode
-- =============================================================================
-- RAW     = 会中主持页平铺展示（无近三月/状态归纳）
-- GROUPED = 近三个月（按创建日期）+ 已完成/延期/进行中（默认，与现网一致）
--
-- 生效范围：meeting-server GET agenda-doc-content 拉取 bitable 时；bot/MCP 不读此列
-- 文档：docs/weekly-matter-comparison.md § bitable_display_mode
-- =============================================================================

SET @col_bdm := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'int_matter_progress_doc_config'
      AND column_name = 'bitable_display_mode'
);
SET @sql_bdm := IF(@col_bdm = 0,
    'ALTER TABLE int_matter_progress_doc_config ADD COLUMN bitable_display_mode VARCHAR(16) NOT NULL DEFAULT ''GROUPED'' COMMENT ''RAW=会中平铺；GROUPED=近三月+完成/延期/进行中'' AFTER config_role',
    'SELECT ''bitable_display_mode exists'' AS _skip');
PREPARE stmt_bdm FROM @sql_bdm;
EXECUTE stmt_bdm;
DEALLOCATE PREPARE stmt_bdm;

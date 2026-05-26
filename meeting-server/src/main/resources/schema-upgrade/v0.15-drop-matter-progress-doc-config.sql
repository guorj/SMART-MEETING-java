-- =============================================================================
-- v0.15 删除已废弃表 int_matter_progress_doc_config
-- =============================================================================
-- 前置：v0.14 已合并数据至 host_agenda v2，且应用已停写旧表。
-- 新环境请直接使用 schema.sql（已无本表），勿执行本脚本。
-- =============================================================================

SET @tbl := (
    SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'int_matter_progress_doc_config'
);
SET @sql_drop := IF(@tbl > 0,
    'DROP TABLE int_matter_progress_doc_config',
    'SELECT ''int_matter_progress_doc_config already dropped'' AS _skip');
PREPARE stmt_drop FROM @sql_drop;
EXECUTE stmt_drop;
DEALLOCATE PREPARE stmt_drop;

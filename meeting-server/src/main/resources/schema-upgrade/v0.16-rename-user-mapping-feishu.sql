-- =============================================================================
-- v0.16 将 int_user_mapping 重命名为 int_user_mapping_feishu
-- =============================================================================
-- 新环境请直接使用 schema.sql（表名已为 int_user_mapping_feishu），勿执行本脚本。
-- 已有库：在部署对应代码前或后执行均可（幂等）；仅当旧表存在且新表不存在时 RENAME。
-- =============================================================================

SET @has_old := (
    SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'int_user_mapping'
);
SET @has_new := (
    SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'int_user_mapping_feishu'
);
SET @sql_rename := IF(@has_old > 0 AND @has_new = 0,
    'RENAME TABLE int_user_mapping TO int_user_mapping_feishu',
    IF(@has_new > 0,
        'SELECT ''int_user_mapping_feishu already exists'' AS _skip',
        'SELECT ''int_user_mapping not found, skip rename'' AS _skip'));
PREPARE stmt_rename FROM @sql_rename;
EXECUTE stmt_rename;
DEALLOCATE PREPARE stmt_rename;

-- ============================================================
-- v0.20 删除 int_user_mapping_feishu 的 feishu_open_id / feishu_union_id
-- 前置：必须先执行 v0.19-normalize-feishu-userid.sql 且 v0.19-verify 残留为 0
-- 兼容：MySQL 5.7+（information_schema 判断列/索引是否存在）
-- ============================================================

-- 删除 idx_feishu_open_id（若存在）
SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'int_user_mapping_feishu'
      AND index_name = 'idx_feishu_open_id'
);
SET @sql_drop_idx := IF(@idx_exists > 0,
    'ALTER TABLE int_user_mapping_feishu DROP INDEX idx_feishu_open_id',
    'SELECT ''skip drop idx_feishu_open_id'' AS _skip');
PREPARE stmt FROM @sql_drop_idx;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 删除 feishu_open_id 列（若存在）
SET @col_open := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'int_user_mapping_feishu'
      AND column_name = 'feishu_open_id'
);
SET @sql_drop_open := IF(@col_open > 0,
    'ALTER TABLE int_user_mapping_feishu DROP COLUMN feishu_open_id',
    'SELECT ''skip drop feishu_open_id'' AS _skip');
PREPARE stmt FROM @sql_drop_open;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 删除 feishu_union_id 列（若存在）
SET @col_union := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'int_user_mapping_feishu'
      AND column_name = 'feishu_union_id'
);
SET @sql_drop_union := IF(@col_union > 0,
    'ALTER TABLE int_user_mapping_feishu DROP COLUMN feishu_union_id',
    'SELECT ''skip drop feishu_union_id'' AS _skip');
PREPARE stmt FROM @sql_drop_union;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 可选：feishu_user_id 唯一约束（需先清洗重复 NULL/空值）
-- ALTER TABLE int_user_mapping_feishu ADD UNIQUE INDEX uk_feishu_user_id (feishu_user_id);

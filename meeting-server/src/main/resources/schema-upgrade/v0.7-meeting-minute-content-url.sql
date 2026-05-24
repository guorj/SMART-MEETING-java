-- v0.7：int_meeting_minute 增加正文对应文档链接
-- 执行: ProdSchemaMigrate --apply src/main/resources/schema-upgrade/v0.7-meeting-minute-content-url.sql

SET @col_cu := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'int_meeting_minute'
      AND column_name = 'content_url'
);
SET @sql_cu := IF(@col_cu = 0,
    'ALTER TABLE int_meeting_minute ADD COLUMN content_url VARCHAR(300) NULL COMMENT ''正文对应文档链接（如飞书纪要 URL）'' AFTER content_markdown',
    'SELECT ''content_url exists'' AS _skip');
PREPARE stmt_cu FROM @sql_cu;
EXECUTE stmt_cu;
DEALLOCATE PREPARE stmt_cu;

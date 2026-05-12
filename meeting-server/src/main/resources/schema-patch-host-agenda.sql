-- 拆分 host_agenda：已上线库执行一次（幂等）；新环境请直接以 schema.sql 全量建表
SET @exist := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'int_meeting'
      AND column_name = 'host_agenda'
);
SET @sqlstmt := IF(
    @exist = 0,
    'ALTER TABLE int_meeting ADD COLUMN host_agenda JSON NULL COMMENT ''AI主持专用：{"items":[{"title","minutes"}]}'' AFTER agenda',
    'SELECT 1'
);
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

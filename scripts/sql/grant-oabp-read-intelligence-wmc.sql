-- =============================================================================
-- 授予 oabp 账号读取 intelligence 会前通报表的权限（跨库 oabpTaskSql 前置条件）
-- 背景：
--   meeting-server meeting.datasource.external.oabp 默认以 oabp 账号连 oabp_pro 库。
--   会序 oabpTaskSql 若引用 intelligence.int_weekly_matter_comparison_* 跨库前缀，
--   oabp 账号必须有 SELECT 权限，否则 MySQL 报 SELECT command denied (SQLState 42000)，
--   Spring 包装为 BadSqlGrammarException（"bad SQL grammar"），易被误判为语法错误。
--   weekly-matter-comparison-agenda-display.sql 顶部注释已提示此要求，本脚本补齐 bootstrap 缺口。
-- 执行：需 MySQL 管理员账号（如 root）执行一次；幂等可重复
-- =============================================================================
-- 授予 oabp 账号对 intelligence 会前通报 run/item 两表的只读权限
GRANT SELECT ON intelligence.int_weekly_matter_comparison_run  TO 'oabp'@'%';
GRANT SELECT ON intelligence.int_weekly_matter_comparison_item TO 'oabp'@'%';

-- 如未来 oabpTaskSql 还需引用 job 表（last_run_id 指针等），一并放开
GRANT SELECT ON intelligence.int_weekly_matter_comparison_job  TO 'oabp'@'%';

FLUSH PRIVILEGES;

-- 验证：以 oabp 账号登录后执行（应返回列定义而非权限错误）
-- SHOW COLUMNS FROM intelligence.int_weekly_matter_comparison_item;

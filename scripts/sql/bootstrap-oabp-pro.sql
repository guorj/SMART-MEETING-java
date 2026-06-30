-- oabp_pro 库初始化（需 MySQL 管理员执行）
-- 用途：meeting-server meeting.datasource.external.oabp 默认连 oabp_pro
-- 执行后运行：
--   cd smart-meeting-java/scripts
--   set DB_OABP_NAME=oabp_pro  (PowerShell: $env:DB_OABP_NAME="oabp_pro")
--   npm run import-feishu-base-jq-todos
--   npm run import-feishu-base

CREATE DATABASE IF NOT EXISTS oabp_pro
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

-- 按实际部署账号调整（示例与 intelligence 联调账号一致）
GRANT ALL PRIVILEGES ON oabp_pro.* TO 'oabp'@'%';
FLUSH PRIVILEGES;

-- 从 oabp 复制表结构（不复制数据；导入脚本会写入飞书快照）
-- 若 oabp 中尚无某表，请按 oabp 应用 DDL 在 oabp_pro 建表后再导入。

CREATE TABLE IF NOT EXISTS oabp_pro.jq_todos_task LIKE oabp.jq_todos_task;
CREATE TABLE IF NOT EXISTS oabp_pro.jq_todos_subtask LIKE oabp.jq_todos_subtask;
CREATE TABLE IF NOT EXISTS oabp_pro.jq_todos_task_followup LIKE oabp.jq_todos_task_followup;
CREATE TABLE IF NOT EXISTS oabp_pro.jq_project_task_tracking LIKE oabp.jq_project_task_tracking;

-- 可选：复制 system_users 到 oabp_pro（导入脚本默认跨库查 oabp.system_users，非必须）
-- CREATE TABLE IF NOT EXISTS oabp_pro.system_users LIKE oabp.system_users;
-- INSERT INTO oabp_pro.system_users SELECT * FROM oabp.system_users;

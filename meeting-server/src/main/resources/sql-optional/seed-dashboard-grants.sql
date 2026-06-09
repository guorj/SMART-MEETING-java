-- ===========================================================================
-- 幂等种子：Dashboard 白名单配置（dashboard.user_grants）
-- ===========================================================================
-- 用途：在 int_meeting_system_config 表中插入默认的空白名单配置。
--       defaultDeny=true + entries=[] 表示所有人都被拒绝，
--       管理员通过 admin 界面或手动 UPDATE 添加授权条目。
--
-- 执行方式：
--   1. 手动执行（推荐首次部署时）
--   2. 或通过 meeting-admin 的 SystemConfig 管理界面创建
--
-- 幂等保证：
--   INSERT ... ON DUPLICATE KEY UPDATE 仅在 config_key 不存在时插入，
--   已存在则不覆盖（保留管理员已维护的白名单）。
-- ===========================================================================

INSERT INTO int_meeting_system_config (config_key, category, value_json, description, updated_at)
VALUES (
    'dashboard.user_grants',
    'dashboard',
    '{"defaultDeny":true,"entries":[]}',
    '会议管理前台白名单：defaultDeny=true 默认拒绝未授权用户；entries 中 enabled=true 的用户可进入 Dashboard，子集可建会/结束会/注册声纹',
    CURRENT_TIMESTAMP
)
ON DUPLICATE KEY UPDATE
    config_key = config_key;
-- 注：ON DUPLICATE KEY UPDATE config_key = config_key 为 no-op，
-- 仅确保幂等性（已有记录不被覆盖）。

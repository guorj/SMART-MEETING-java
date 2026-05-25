-- =============================================================================
-- v0.12 会议系统参数表（管理后台 meeting-admin-server 写入，meeting-server 热加载）
-- =============================================================================
CREATE TABLE IF NOT EXISTS int_meeting_system_config (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    config_key   VARCHAR(128)    NOT NULL COMMENT '参数键，如 meeting.host.roll-call-enabled',
    category     VARCHAR(32)     NOT NULL DEFAULT 'host' COMMENT '分组：host|asr|cache|notification',
    value_json   JSON            NOT NULL COMMENT '参数值 JSON（布尔/数字/字符串）',
    description  VARCHAR(500)    NULL COMMENT '说明',
    updated_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_meeting_system_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='会议系统可热改参数（非密钥）；Admin 写库，会中进程 reload';

-- =============================================================================
-- v0.13 系统参数变更审计（meeting-admin-server 写入）
-- =============================================================================
CREATE TABLE IF NOT EXISTS int_meeting_system_config_audit (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    config_key   VARCHAR(128)    NOT NULL COMMENT '参数键',
    action       VARCHAR(16)     NOT NULL COMMENT 'UPSERT|DELETE',
    old_value_json JSON          NULL COMMENT '变更前值',
    new_value_json JSON          NULL COMMENT '变更后值',
    operator     VARCHAR(64)     NULL COMMENT '操作者标识（首期 admin）',
    created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_config_audit_key_time (config_key, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='系统参数变更审计';

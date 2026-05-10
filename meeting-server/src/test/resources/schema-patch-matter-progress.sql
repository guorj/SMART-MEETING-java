-- 事项进度通报配置表（与 schema.sql 定义一致，可重复执行）
CREATE TABLE IF NOT EXISTS int_matter_progress_doc_config (
    id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    config_name          VARCHAR(64)   NOT NULL COMMENT '逻辑配置名，全局唯一',
    feishu_doc_url       VARCHAR(2000) NULL     COMMENT '飞书 Docx HTTPS 链接，需含 /docx/{document_id}；可与 token 二选一',
    feishu_doc_token     VARCHAR(512)  NULL     COMMENT '飞书 Docx document_id；优先于从 URL 解析',
    enabled              TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '0关闭 1启用',
    created_at           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_matter_progress_config_name (config_name),
    KEY idx_matter_progress_enabled_id (enabled, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='事项进度通报：飞书文档配置';

INSERT INTO int_matter_progress_doc_config (id, config_name, feishu_doc_url, feishu_doc_token, enabled)
VALUES (1, 'default', NULL, NULL, 1)
ON DUPLICATE KEY UPDATE enabled = VALUES(enabled);

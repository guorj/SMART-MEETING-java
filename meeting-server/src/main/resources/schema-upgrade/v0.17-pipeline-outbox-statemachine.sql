-- =============================================================================
-- v0.17 Phase1-5 基础设施升级
-- 目标：
--   1) 新增 Outbox 事务外盒表，保障事件可靠投递
--   2) 新增 Pipeline 模板/步骤/运行时表，支持 admin 流水线配置
--   3) 新增命令幂等表，避免重复执行
-- 说明：
--   - 全部 DDL 采用 IF NOT EXISTS，支持幂等执行
--   - 不覆盖历史业务数据，仅新增结构
-- =============================================================================

CREATE TABLE IF NOT EXISTS int_event_outbox (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    aggregate_type VARCHAR(64)     NOT NULL COMMENT '聚合类型，如 MEETING',
    aggregate_id   VARCHAR(64)     NOT NULL COMMENT '聚合 ID',
    event_type     VARCHAR(64)     NOT NULL COMMENT '事件类型',
    event_key      VARCHAR(128)    NOT NULL COMMENT '幂等键',
    payload_json   JSON            NOT NULL COMMENT '事件载荷',
    status         VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING|RETRY|PUBLISHED|FAILED',
    retry_count    INT             NOT NULL DEFAULT 0 COMMENT '重试次数',
    next_retry_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下次重试时间',
    error_message  VARCHAR(500)    NULL COMMENT '错误摘要',
    published_at   DATETIME        NULL COMMENT '最终投递成功时间',
    created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_event_outbox_key (event_key),
    KEY idx_event_outbox_scan (status, next_retry_at),
    KEY idx_event_outbox_aggregate (aggregate_type, aggregate_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='事务外盒事件表';

CREATE TABLE IF NOT EXISTS int_pipeline_template (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '模板 ID',
    template_code  VARCHAR(64)     NOT NULL COMMENT '模板编码',
    template_name  VARCHAR(120)    NOT NULL COMMENT '模板名称',
    stage          VARCHAR(16)     NOT NULL COMMENT 'PRE|MID|POST',
    enabled        TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否启用',
    version_no     INT             NOT NULL DEFAULT 1 COMMENT '版本号',
    description    VARCHAR(500)    NULL COMMENT '模板说明',
    created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_pipeline_template_code (template_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Pipeline 模板定义';

CREATE TABLE IF NOT EXISTS int_pipeline_step (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '步骤 ID',
    template_id      BIGINT UNSIGNED NOT NULL COMMENT '模板 ID',
    step_code        VARCHAR(64)     NOT NULL COMMENT '步骤编码',
    step_name        VARCHAR(120)    NOT NULL COMMENT '步骤名称',
    step_type        VARCHAR(64)     NOT NULL COMMENT '执行器类型',
    stage            VARCHAR(16)     NOT NULL COMMENT 'PRE|MID|POST',
    order_no         INT             NOT NULL COMMENT '顺序',
    timeout_seconds  INT             NULL COMMENT '超时时间（秒）',
    config_json      JSON            NULL COMMENT '步骤配置',
    enabled          TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_pipeline_step_code (template_id, step_code),
    KEY idx_pipeline_step_template_order (template_id, order_no),
    CONSTRAINT fk_pipeline_step_template FOREIGN KEY (template_id)
        REFERENCES int_pipeline_template (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Pipeline 步骤定义';

CREATE TABLE IF NOT EXISTS int_pipeline_step_execution (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '执行 ID',
    meeting_id       VARCHAR(36)     NOT NULL COMMENT '会议 ID',
    template_id      BIGINT UNSIGNED NOT NULL COMMENT '模板 ID',
    step_id          BIGINT UNSIGNED NOT NULL COMMENT '步骤 ID',
    stage            VARCHAR(16)     NOT NULL COMMENT 'PRE|MID|POST',
    status           VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING|RUNNING|SUCCESS|FAILED|WAITING|TIMEOUT',
    retry_count      INT             NOT NULL DEFAULT 0 COMMENT '重试次数',
    max_retries      INT             NOT NULL DEFAULT 3 COMMENT '最大重试',
    timeout_at       DATETIME        NULL COMMENT '超时截止时间',
    started_at       DATETIME        NULL COMMENT '开始执行时间',
    ended_at         DATETIME        NULL COMMENT '结束执行时间',
    last_error       VARCHAR(500)    NULL COMMENT '最后错误',
    context_json     JSON            NULL COMMENT '执行上下文',
    created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_pipeline_exec_meeting_stage (meeting_id, stage, status),
    KEY idx_pipeline_exec_timeout (status, timeout_at),
    CONSTRAINT fk_pipeline_exec_template FOREIGN KEY (template_id)
        REFERENCES int_pipeline_template (id) ON DELETE CASCADE,
    CONSTRAINT fk_pipeline_exec_step FOREIGN KEY (step_id)
        REFERENCES int_pipeline_step (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Pipeline 步骤运行时';

CREATE TABLE IF NOT EXISTS int_processed_command (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    command_key     VARCHAR(128)    NOT NULL COMMENT '命令幂等键',
    command_type    VARCHAR(64)     NOT NULL COMMENT '命令类型',
    aggregate_type  VARCHAR(64)     NOT NULL COMMENT '聚合类型',
    aggregate_id    VARCHAR(64)     NOT NULL COMMENT '聚合 ID',
    processed_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '处理时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_processed_command_key (command_key),
    KEY idx_processed_command_aggregate (aggregate_type, aggregate_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='幂等命令处理记录';

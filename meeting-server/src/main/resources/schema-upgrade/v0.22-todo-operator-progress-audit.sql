-- =============================================================================
-- v0.22 待办经办人、进度时间线、附件、Admin 审计
-- =============================================================================

ALTER TABLE int_meeting_todo
    ADD COLUMN operator_id   VARCHAR(64)  NULL COMMENT '经办人飞书user_id' AFTER assignee_name,
    ADD COLUMN operator_name VARCHAR(100) NULL COMMENT '经办人姓名' AFTER operator_id,
    ADD INDEX idx_todo_operator (operator_id);

CREATE TABLE IF NOT EXISTS int_meeting_todo_progress (
    id               VARCHAR(36)  NOT NULL PRIMARY KEY COMMENT '进度记录UUID',
    todo_id          VARCHAR(36)  NOT NULL COMMENT '待办ID',
    author_id        VARCHAR(64)  NOT NULL COMMENT '提交人飞书user_id',
    author_name      VARCHAR(100) NULL     COMMENT '提交人姓名',
    author_role      VARCHAR(20)  NOT NULL COMMENT 'ASSIGNEE|OPERATOR',
    progress_text    TEXT         NOT NULL COMMENT '进度说明',
    progress_percent TINYINT      NULL     COMMENT '进度百分比0-100',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (todo_id) REFERENCES int_meeting_todo(id) ON DELETE CASCADE,
    INDEX idx_todo_progress_todo (todo_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='待办进度时间线';

CREATE TABLE IF NOT EXISTS int_meeting_todo_attachment (
    id            VARCHAR(36)  NOT NULL PRIMARY KEY COMMENT '附件UUID',
    todo_id       VARCHAR(36)  NOT NULL COMMENT '待办ID',
    progress_id   VARCHAR(36)  NULL     COMMENT '关联进度记录ID',
    uploader_id   VARCHAR(64)  NOT NULL COMMENT '上传人飞书user_id',
    uploader_name VARCHAR(100) NULL     COMMENT '上传人姓名',
    file_name     VARCHAR(255) NOT NULL COMMENT '原始文件名',
    storage_path  VARCHAR(500) NOT NULL COMMENT '服务器存储相对路径',
    file_size     BIGINT       NOT NULL DEFAULT 0 COMMENT '文件大小字节',
    mime_type     VARCHAR(128) NULL     COMMENT 'MIME类型',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (todo_id) REFERENCES int_meeting_todo(id) ON DELETE CASCADE,
    INDEX idx_todo_attachment_todo (todo_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='待办附件';

CREATE TABLE IF NOT EXISTS int_meeting_todo_audit (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    todo_id        VARCHAR(36)     NOT NULL COMMENT '待办ID',
    action         VARCHAR(32)     NOT NULL COMMENT 'ADMIN_FORCE_STATUS|ADMIN_ASSIGN|ADMIN_DELETE',
    old_status     VARCHAR(20)     NULL     COMMENT '变更前状态',
    new_status     VARCHAR(20)     NULL     COMMENT '变更后状态',
    operator_id    VARCHAR(64)     NULL     COMMENT 'Admin操作者飞书user_id',
    operator_name  VARCHAR(100)    NULL     COMMENT 'Admin操作者姓名',
    reason         VARCHAR(500)    NOT NULL COMMENT '强制操作原因',
    payload_json   JSON            NULL     COMMENT '其他变更快照',
    created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_todo_audit_todo (todo_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='待办Admin强制操作审计';

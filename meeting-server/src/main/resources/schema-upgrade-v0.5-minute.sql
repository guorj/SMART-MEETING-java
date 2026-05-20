-- v0.5：会议纪要库内持久化（与飞书 doc_url 双写）
-- 执行: ProdSchemaMigrate --apply schema-upgrade-v0.5-minute.sql

CREATE TABLE IF NOT EXISTS int_meeting_minute (
    meeting_id          VARCHAR(36)   NOT NULL PRIMARY KEY COMMENT '会议UUID',
    content_markdown    LONGTEXT      NOT NULL COMMENT '纪要正文 Markdown',
    content_length      INT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '正文字符数',
    generation_status   VARCHAR(20)   NOT NULL DEFAULT 'READY' COMMENT 'READY|FAILED|PARTIAL',
    generated_at        DATETIME      NOT NULL COMMENT '纪要生成时间',
    updated_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (meeting_id) REFERENCES int_meeting(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会议纪要正文（最新一版）';

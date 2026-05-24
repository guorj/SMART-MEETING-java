-- ============================================================
-- intelligence 库 — 结构差异补齐（幂等）
-- 生成: 2026-05-21
-- 对照: prod-schema-snapshot.json vs 项目最终 DDL
-- 执行前请备份
-- ============================================================

USE intelligence;

-- -----------------------------------------------------------------------------
-- 1. int_meeting_participant — 混合参会三列（migration_20260515）
-- -----------------------------------------------------------------------------
SET @col_am := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'int_meeting_participant'
      AND column_name = 'attendance_mode'
);
SET @sql_am := IF(@col_am = 0,
    'ALTER TABLE int_meeting_participant
        ADD COLUMN attendance_mode VARCHAR(20) NOT NULL DEFAULT ''OFFLINE''
            COMMENT ''OFFLINE=线下答到点名 ONLINE=线上链接盘点'' AFTER status,
        ADD COLUMN checked_in_at DATETIME NULL COMMENT ''到场登记时间'' AFTER attendance_mode,
        ADD COLUMN check_in_source VARCHAR(30) NULL COMMENT ''AUTO_ONLINE|ROLL_CALL|MANUAL|TIMEOUT'' AFTER checked_in_at',
    'SELECT ''int_meeting_participant hybrid columns exist'' AS _skip');
PREPARE stmt_am FROM @sql_am;
EXECUTE stmt_am;
DEALLOCATE PREPARE stmt_am;

-- -----------------------------------------------------------------------------
-- 2. int_scheduled_push_log — Flyway V2 已读轮询列
-- -----------------------------------------------------------------------------
SET @col_fmid := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'int_scheduled_push_log'
      AND column_name = 'feishu_message_id'
);
SET @sql_v2 := IF(@col_fmid = 0,
    'ALTER TABLE int_scheduled_push_log
        ADD COLUMN feishu_message_id VARCHAR(64) NULL AFTER send_time,
        ADD COLUMN read_poll_status VARCHAR(20) NULL AFTER feishu_message_id,
        ADD COLUMN read_count INT NOT NULL DEFAULT 0 AFTER read_poll_status,
        ADD COLUMN last_read_poll_at TIMESTAMP NULL AFTER read_count,
        ADD COLUMN read_poll_error VARCHAR(500) NULL AFTER last_read_poll_at',
    'SELECT ''int_scheduled_push_log V2 columns exist'' AS _skip');
PREPARE stmt_v2 FROM @sql_v2;
EXECUTE stmt_v2;
DEALLOCATE PREPARE stmt_v2;

SET @idx_msg := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'int_scheduled_push_log'
      AND index_name = 'idx_int_sch_log_msg_id'
);
SET @sql_idx_msg := IF(@idx_msg = 0,
    'CREATE INDEX idx_int_sch_log_msg_id ON int_scheduled_push_log(feishu_message_id)',
    'SELECT ''idx_int_sch_log_msg_id exists'' AS _skip');
PREPARE stmt_idx_msg FROM @sql_idx_msg;
EXECUTE stmt_idx_msg;
DEALLOCATE PREPARE stmt_idx_msg;

SET @idx_rp := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'int_scheduled_push_log'
      AND index_name = 'idx_int_sch_log_read_poll'
);
SET @sql_idx_rp := IF(@idx_rp = 0,
    'CREATE INDEX idx_int_sch_log_read_poll ON int_scheduled_push_log(read_poll_status, send_time)',
    'SELECT ''idx_int_sch_log_read_poll exists'' AS _skip');
PREPARE stmt_idx_rp FROM @sql_idx_rp;
EXECUTE stmt_idx_rp;
DEALLOCATE PREPARE stmt_idx_rp;

-- -----------------------------------------------------------------------------
-- 3. int_scheduled_push_log — Flyway V4 会议事件推送列
-- -----------------------------------------------------------------------------
SET @col_mid := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'int_scheduled_push_log'
      AND column_name = 'meeting_id'
);
SET @sql_v4 := IF(@col_mid = 0,
    'ALTER TABLE int_scheduled_push_log
        ADD COLUMN meeting_id VARCHAR(36) NULL COMMENT ''关联会议 UUID（EVENT 推送）'' AFTER task_name,
        ADD COLUMN source_event_type VARCHAR(40) NULL COMMENT ''MINUTE_READY|TODO_SYNC|TODO_PROGRESS 等'' AFTER meeting_id,
        ADD COLUMN idempotency_key VARCHAR(128) NULL COMMENT ''业务幂等键'' AFTER source_event_type',
    'SELECT ''int_scheduled_push_log V4 columns exist'' AS _skip');
PREPARE stmt_v4 FROM @sql_v4;
EXECUTE stmt_v4;
DEALLOCATE PREPARE stmt_v4;

SET @idx_mtg := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'int_scheduled_push_log'
      AND index_name = 'idx_int_sch_log_meeting_id'
);
SET @sql_idx_mtg := IF(@idx_mtg = 0,
    'CREATE INDEX idx_int_sch_log_meeting_id ON int_scheduled_push_log(meeting_id)',
    'SELECT ''idx_int_sch_log_meeting_id exists'' AS _skip');
PREPARE stmt_idx_mtg FROM @sql_idx_mtg;
EXECUTE stmt_idx_mtg;
DEALLOCATE PREPARE stmt_idx_mtg;

SET @idx_idem := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'int_scheduled_push_log'
      AND index_name = 'idx_int_sch_log_idempotency'
);
SET @sql_idx_idem := IF(@idx_idem = 0,
    'CREATE INDEX idx_int_sch_log_idempotency ON int_scheduled_push_log(idempotency_key)',
    'SELECT ''idx_int_sch_log_idempotency exists'' AS _skip');
PREPARE stmt_idx_idem FROM @sql_idx_idem;
EXECUTE stmt_idx_idem;
DEALLOCATE PREPARE stmt_idx_idem;

-- -----------------------------------------------------------------------------
-- 4. int_scheduled_push_log_read_user — Flyway V2+V3
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS int_scheduled_push_log_read_user (
    id           VARCHAR(36)  NOT NULL PRIMARY KEY,
    push_log_id  VARCHAR(36)  NOT NULL,
    user_id_type VARCHAR(20)  NOT NULL DEFAULT 'open_id',
    user_id      VARCHAR(100) NOT NULL,
    read_at      TIMESTAMP    NOT NULL,
    tenant_key   VARCHAR(64)  NULL,
    user_name    VARCHAR(100) NULL,
    UNIQUE KEY uk_push_log_user (push_log_id, user_id),
    INDEX idx_int_sch_log_read_user_log (push_log_id),
    INDEX idx_int_sch_log_read_user_at (read_at),
    CONSTRAINT fk_sch_log_read_user_log
        FOREIGN KEY (push_log_id) REFERENCES int_scheduled_push_log(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='推送消息已读用户明细';

SET @col_uname := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'int_scheduled_push_log_read_user'
      AND column_name = 'user_name'
);
SET @sql_uname := IF(@col_uname = 0,
    'ALTER TABLE int_scheduled_push_log_read_user ADD COLUMN user_name VARCHAR(100) NULL AFTER tenant_key',
    'SELECT ''user_name exists'' AS _skip');
PREPARE stmt_uname FROM @sql_uname;
EXECUTE stmt_uname;
DEALLOCATE PREPARE stmt_uname;

-- -----------------------------------------------------------------------------
-- 5. int_scheduled_push_task — updated_at ON UPDATE（若缺失）
-- -----------------------------------------------------------------------------
SET @col_uu := (
    SELECT EXTRA FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'int_scheduled_push_task'
      AND column_name = 'updated_at'
);
-- 仅当 EXTRA 不含 on update 时修改（MySQL 8）
SET @sql_uu := IF(@col_uu IS NOT NULL AND @col_uu NOT LIKE '%on update%',
    'ALTER TABLE int_scheduled_push_task MODIFY COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP',
    'SELECT ''updated_at on update ok or skip'' AS _skip');
PREPARE stmt_uu FROM @sql_uu;
EXECUTE stmt_uu;
DEALLOCATE PREPARE stmt_uu;

SELECT 'schema-diff-migration-20260521 completed' AS result;

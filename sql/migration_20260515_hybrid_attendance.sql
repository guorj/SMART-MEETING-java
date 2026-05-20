-- 混合参会：线下点名 + 线上接入盘点
ALTER TABLE int_meeting_participant
    ADD COLUMN attendance_mode VARCHAR(20) NOT NULL DEFAULT 'OFFLINE'
        COMMENT 'OFFLINE=线下答到点名 ONLINE=线上链接盘点' AFTER status,
    ADD COLUMN checked_in_at DATETIME NULL COMMENT '到场登记时间' AFTER attendance_mode,
    ADD COLUMN check_in_source VARCHAR(30) NULL COMMENT 'AUTO_ONLINE|ROLL_CALL|MANUAL|TIMEOUT' AFTER checked_in_at;

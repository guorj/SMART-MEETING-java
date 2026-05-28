-- ============================================================
-- v0.19 业务表用户标识归一化：open_id -> user_id
-- 目标：
--   1) 将 int_meeting.creator_id / int_meeting_participant.user_id /
--      int_meeting_todo.assignee_id / int_voiceprint.feishu_user_id
--      中可映射的 open_id 迁移为 feishu_user_id
--   2) 全程保留回滚数据（*_bak_* 表）
--
-- 兼容：MySQL 5.7+（避免使用 ADD COLUMN IF NOT EXISTS）
-- 幂等：可重复执行（备份表主键 + INSERT IGNORE）
-- ============================================================

START TRANSACTION;

-- 0) 迁移前置校验（人工确认）
-- 仅当 int_user_mapping_feishu.feishu_user_id 有值时才会执行替换。
-- 若 feishu_user_id 大量为空，请先补齐映射后再执行本脚本。

-- 1) 备份表（用于回滚）
CREATE TABLE IF NOT EXISTS int_mig_bak_meeting_creator (
    meeting_id      VARCHAR(36)  NOT NULL PRIMARY KEY COMMENT 'int_meeting.id',
    old_creator_id  VARCHAR(64)  NOT NULL COMMENT '迁移前 creator_id',
    backed_up_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '备份时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='v0.19 会议发起人ID迁移备份';

CREATE TABLE IF NOT EXISTS int_mig_bak_participant_user (
    participant_id  VARCHAR(36)  NOT NULL PRIMARY KEY COMMENT 'int_meeting_participant.id',
    old_user_id     VARCHAR(64)  NOT NULL COMMENT '迁移前 user_id',
    backed_up_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '备份时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='v0.19 参会人ID迁移备份';

CREATE TABLE IF NOT EXISTS int_mig_bak_todo_assignee (
    todo_id          VARCHAR(36)  NOT NULL PRIMARY KEY COMMENT 'int_meeting_todo.id',
    old_assignee_id  VARCHAR(64)  NOT NULL COMMENT '迁移前 assignee_id',
    backed_up_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '备份时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='v0.19 待办责任人ID迁移备份';

CREATE TABLE IF NOT EXISTS int_mig_bak_voiceprint_feishu_user (
    voiceprint_id         VARCHAR(36)  NOT NULL PRIMARY KEY COMMENT 'int_voiceprint.id',
    old_feishu_user_id    VARCHAR(100) NOT NULL COMMENT '迁移前 feishu_user_id',
    backed_up_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '备份时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='v0.19 声纹飞书ID迁移备份';

-- 2) 备份即将迁移的数据
INSERT IGNORE INTO int_mig_bak_meeting_creator (meeting_id, old_creator_id)
SELECT m.id, m.creator_id
FROM int_meeting m
JOIN int_user_mapping_feishu um ON um.feishu_open_id = m.creator_id
WHERE um.feishu_user_id IS NOT NULL
  AND um.feishu_user_id <> ''
  AND um.feishu_user_id <> m.creator_id;

INSERT IGNORE INTO int_mig_bak_participant_user (participant_id, old_user_id)
SELECT p.id, p.user_id
FROM int_meeting_participant p
JOIN int_user_mapping_feishu um ON um.feishu_open_id = p.user_id
WHERE um.feishu_user_id IS NOT NULL
  AND um.feishu_user_id <> ''
  AND um.feishu_user_id <> p.user_id;

INSERT IGNORE INTO int_mig_bak_todo_assignee (todo_id, old_assignee_id)
SELECT t.id, t.assignee_id
FROM int_meeting_todo t
JOIN int_user_mapping_feishu um ON um.feishu_open_id = t.assignee_id
WHERE um.feishu_user_id IS NOT NULL
  AND um.feishu_user_id <> ''
  AND um.feishu_user_id <> t.assignee_id;

INSERT IGNORE INTO int_mig_bak_voiceprint_feishu_user (voiceprint_id, old_feishu_user_id)
SELECT v.id, v.feishu_user_id
FROM int_voiceprint v
JOIN int_user_mapping_feishu um ON um.feishu_open_id = v.feishu_user_id
WHERE um.feishu_user_id IS NOT NULL
  AND um.feishu_user_id <> ''
  AND um.feishu_user_id <> v.feishu_user_id;

-- 3) 正式迁移（仅替换可映射且目标非空的记录）
UPDATE int_meeting m
JOIN int_user_mapping_feishu um ON um.feishu_open_id = m.creator_id
SET m.creator_id = um.feishu_user_id
WHERE um.feishu_user_id IS NOT NULL
  AND um.feishu_user_id <> ''
  AND um.feishu_user_id <> m.creator_id;

UPDATE int_meeting_participant p
JOIN int_user_mapping_feishu um ON um.feishu_open_id = p.user_id
SET p.user_id = um.feishu_user_id
WHERE um.feishu_user_id IS NOT NULL
  AND um.feishu_user_id <> ''
  AND um.feishu_user_id <> p.user_id;

UPDATE int_meeting_todo t
JOIN int_user_mapping_feishu um ON um.feishu_open_id = t.assignee_id
SET t.assignee_id = um.feishu_user_id
WHERE um.feishu_user_id IS NOT NULL
  AND um.feishu_user_id <> ''
  AND um.feishu_user_id <> t.assignee_id;

UPDATE int_voiceprint v
JOIN int_user_mapping_feishu um ON um.feishu_open_id = v.feishu_user_id
SET v.feishu_user_id = um.feishu_user_id
WHERE um.feishu_user_id IS NOT NULL
  AND um.feishu_user_id <> ''
  AND um.feishu_user_id <> v.feishu_user_id;

COMMIT;

-- 4) 迁移后核对（手工执行）
-- 4.1 仍然是 open_id 形态（ou_/on_）的残留计数
-- SELECT
--   SUM(CASE WHEN creator_id LIKE 'ou\\_%' OR creator_id LIKE 'on\\_%' THEN 1 ELSE 0 END) AS meeting_creator_open_like
-- FROM int_meeting;
-- SELECT
--   SUM(CASE WHEN user_id LIKE 'ou\\_%' OR user_id LIKE 'on\\_%' THEN 1 ELSE 0 END) AS participant_user_open_like
-- FROM int_meeting_participant;
-- SELECT
--   SUM(CASE WHEN assignee_id LIKE 'ou\\_%' OR assignee_id LIKE 'on\\_%' THEN 1 ELSE 0 END) AS todo_assignee_open_like
-- FROM int_meeting_todo;
-- SELECT
--   SUM(CASE WHEN feishu_user_id LIKE 'ou\\_%' OR feishu_user_id LIKE 'on\\_%' THEN 1 ELSE 0 END) AS voiceprint_feishu_open_like
-- FROM int_voiceprint;


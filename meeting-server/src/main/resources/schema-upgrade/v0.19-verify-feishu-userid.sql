-- ============================================================
-- v0.19 迁移前/后核验（手工执行，不修改数据）
-- 执行 v0.19-normalize-feishu-userid.sql 前后均可跑
-- ============================================================

-- 1) 映射表：feishu_user_id 为空但仍有 open_id 的行（迁移前应补齐）
SELECT user_id, user_name, feishu_user_id, feishu_open_id
FROM int_user_mapping_feishu
WHERE (feishu_user_id IS NULL OR feishu_user_id = '')
  AND feishu_open_id IS NOT NULL AND feishu_open_id <> '';

-- 2) 业务表残留 open_id 形态（ou_/on_ 前缀）
SELECT 'int_meeting.creator_id' AS src,
       COUNT(*) AS cnt
FROM int_meeting
WHERE creator_id LIKE 'ou\_%' OR creator_id LIKE 'on\_%'
UNION ALL
SELECT 'int_meeting_participant.user_id', COUNT(*)
FROM int_meeting_participant
WHERE user_id LIKE 'ou\_%' OR user_id LIKE 'on\_%'
UNION ALL
SELECT 'int_meeting_todo.assignee_id', COUNT(*)
FROM int_meeting_todo
WHERE assignee_id LIKE 'ou\_%' OR assignee_id LIKE 'on\_%'
UNION ALL
SELECT 'int_voiceprint.feishu_user_id', COUNT(*)
FROM int_voiceprint
WHERE feishu_user_id LIKE 'ou\_%' OR feishu_user_id LIKE 'on\_%';

-- 3) 可通过映射表继续迁移的残留（v0.19 会处理）
SELECT COUNT(*) AS migratable_meeting_creator
FROM int_meeting m
JOIN int_user_mapping_feishu um ON um.feishu_open_id = m.creator_id
WHERE um.feishu_user_id IS NOT NULL AND um.feishu_user_id <> ''
  AND um.feishu_user_id <> m.creator_id;

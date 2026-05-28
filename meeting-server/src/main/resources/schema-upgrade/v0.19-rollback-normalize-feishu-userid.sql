-- ============================================================
-- v0.19 回滚脚本：撤销 open_id -> user_id 归一化
-- 依赖：v0.19-normalize-feishu-userid.sql 生成的备份表
-- 说明：仅回滚已备份过的记录（幂等）
-- ============================================================

START TRANSACTION;

-- 1) 回滚 int_meeting.creator_id
UPDATE int_meeting m
JOIN int_mig_bak_meeting_creator b ON b.meeting_id = m.id
SET m.creator_id = b.old_creator_id;

-- 2) 回滚 int_meeting_participant.user_id
UPDATE int_meeting_participant p
JOIN int_mig_bak_participant_user b ON b.participant_id = p.id
SET p.user_id = b.old_user_id;

-- 3) 回滚 int_meeting_todo.assignee_id
UPDATE int_meeting_todo t
JOIN int_mig_bak_todo_assignee b ON b.todo_id = t.id
SET t.assignee_id = b.old_assignee_id;

-- 4) 回滚 int_voiceprint.feishu_user_id
UPDATE int_voiceprint v
JOIN int_mig_bak_voiceprint_feishu_user b ON b.voiceprint_id = v.id
SET v.feishu_user_id = b.old_feishu_user_id;

COMMIT;

-- 可选：确认是否已恢复
-- SELECT COUNT(*) FROM int_mig_bak_meeting_creator;
-- SELECT COUNT(*) FROM int_mig_bak_participant_user;
-- SELECT COUNT(*) FROM int_mig_bak_todo_assignee;
-- SELECT COUNT(*) FROM int_mig_bak_voiceprint_feishu_user;


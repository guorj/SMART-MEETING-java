-- ============================================================
-- v0.28 int_meeting_type_preset.minute_skill_name
-- 会务类型绑定的纪要生成 OpenClaw Skill 名；Admin「纪要 Skill」页维护
-- 兼容：MySQL 5.7+（information_schema 判断列是否存在）
-- 幂等：列已存在则跳过；不覆盖已有 minute_skill_name
-- ============================================================

SET @col_minute_skill := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'int_meeting_type_preset'
      AND column_name = 'minute_skill_name'
);
SET @sql_add_minute_skill := IF(@col_minute_skill = 0,
    'ALTER TABLE int_meeting_type_preset ADD COLUMN minute_skill_name VARCHAR(64) NULL COMMENT ''纪要生成 OpenClaw Skill 名；NULL 表示走 LLM 降级'' AFTER host_agenda',
    'SELECT ''skip add minute_skill_name'' AS _skip');
PREPARE stmt FROM @sql_add_minute_skill;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE int_meeting_type_preset
SET minute_skill_name = 'tech-committee-minutes'
WHERE code = 2
  AND (minute_skill_name IS NULL OR TRIM(minute_skill_name) = '');

-- ============================================================
-- v0.21 int_meeting_type_preset.schedule_config
-- 机器可读排期规则（weekly/fixed/at_start），驱动 Dashboard 快速开始的 scheduled_time
-- 兼容：MySQL 5.7+（information_schema 判断列是否存在）
-- 幂等：列已存在则跳过；不覆盖已有 schedule_config
-- ============================================================

SET @col_schedule_config := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'int_meeting_type_preset'
      AND column_name = 'schedule_config'
);
SET @sql_add_schedule_config := IF(@col_schedule_config = 0,
    'ALTER TABLE int_meeting_type_preset ADD COLUMN schedule_config JSON NULL COMMENT ''机器可读排期：weekly/fixed/at_start；驱动快速开始 scheduled_time'' AFTER schedule_note',
    'SELECT ''skip add schedule_config'' AS _skip');
PREPARE stmt FROM @sql_add_schedule_config;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 可选：为 code=1 预填 weekly（与 schedule_note「每周一 9:30」对齐），仅当列为空
UPDATE int_meeting_type_preset
SET schedule_config = JSON_OBJECT(
    'type', 'weekly',
    'weekday', 1,
    'hour', 9,
    'minute', 30,
    'preferNextIfPast', true
)
WHERE code = 1
  AND (schedule_config IS NULL OR JSON_TYPE(schedule_config) = 'NULL');

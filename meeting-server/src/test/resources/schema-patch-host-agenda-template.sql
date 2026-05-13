-- 主持议程：preset 表自带 host_agenda；去掉 int_meeting 旧列与 host_agenda_template_meeting_id；可选删系统种子会议

-- 1) 从会议主表删除已废弃列（若存在）
SET @exist_m := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'int_meeting'
      AND column_name = 'host_agenda_template_meeting_id'
);
SET @sql_m := IF(
    @exist_m > 0,
    'ALTER TABLE int_meeting DROP COLUMN host_agenda_template_meeting_id',
    'SELECT 1'
);
PREPARE stmt_m FROM @sql_m;
EXECUTE stmt_m;
DEALLOCATE PREPARE stmt_m;

-- 2) 预设表增加 host_agenda（若不存在）
SET @exist_ha := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'int_meeting_type_preset'
      AND column_name = 'host_agenda'
);
SET @sql_ha := IF(
    @exist_ha = 0,
    'ALTER TABLE int_meeting_type_preset ADD COLUMN host_agenda JSON NULL COMMENT ''AI主持议题模板'' AFTER participants_names',
    'SELECT 1'
);
PREPARE stmt_ha FROM @sql_ha;
EXECUTE stmt_ha;
DEALLOCATE PREPARE stmt_ha;

-- 3) 若仍存在旧列 host_agenda_template_meeting_id：从指向的会议回填 preset.host_agenda
SET @exist_tid := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'int_meeting_type_preset'
      AND column_name = 'host_agenda_template_meeting_id'
);
SET @sql_mig := IF(
    @exist_tid > 0,
    'UPDATE int_meeting_type_preset p INNER JOIN int_meeting m ON m.id = p.host_agenda_template_meeting_id SET p.host_agenda = m.host_agenda WHERE p.code IN (1,2,3,4,5) AND p.host_agenda IS NULL AND m.host_agenda IS NOT NULL',
    'SELECT 1'
);
PREPARE stmt_mig FROM @sql_mig;
EXECUTE stmt_mig;
DEALLOCATE PREPARE stmt_mig;

UPDATE int_meeting_type_preset
SET host_agenda = CAST('{"items":[{"title":"主持议题A","minutes":3},{"title":"事项进度通报","minutes":7}]}' AS JSON)
WHERE code IN (1, 2, 3, 4, 5) AND host_agenda IS NULL;

SET @sql_drop_tid := IF(
    @exist_tid > 0,
    'ALTER TABLE int_meeting_type_preset DROP COLUMN host_agenda_template_meeting_id',
    'SELECT 1'
);
PREPARE stmt_drop_tid FROM @sql_drop_tid;
EXECUTE stmt_drop_tid;
DEALLOCATE PREPARE stmt_drop_tid;

-- 4) 删除仅作旧链路的系统种子会议（若存在）
DELETE FROM int_meeting WHERE id = '00000000-0000-0000-0000-000000000001' AND company = 'SYSTEM';

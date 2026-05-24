-- v0.8：会议子表冗余 preset_type_code，便于区分模板会(1-5)与自定义会(6)
-- 执行: ProdSchemaMigrate --apply src/main/resources/schema-upgrade/v0.8-meeting-preset-type-code.sql

-- int_meeting_participant
SET @col_pp := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'int_meeting_participant'
      AND column_name = 'preset_type_code'
);
SET @sql_pp := IF(@col_pp = 0,
    'ALTER TABLE int_meeting_participant ADD COLUMN preset_type_code TINYINT NULL COMMENT ''1-5 模板会 6 自定义 NULL 未知'' AFTER meeting_id',
    'SELECT ''participant.preset_type_code exists'' AS _skip');
PREPARE stmt_pp FROM @sql_pp;
EXECUTE stmt_pp;
DEALLOCATE PREPARE stmt_pp;

-- int_transcript_segment
SET @col_ts := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'int_transcript_segment'
      AND column_name = 'preset_type_code'
);
SET @sql_ts := IF(@col_ts = 0,
    'ALTER TABLE int_transcript_segment ADD COLUMN preset_type_code TINYINT NULL COMMENT ''1-5 模板会 6 自定义 NULL 未知'' AFTER meeting_id',
    'SELECT ''transcript.preset_type_code exists'' AS _skip');
PREPARE stmt_ts FROM @sql_ts;
EXECUTE stmt_ts;
DEALLOCATE PREPARE stmt_ts;

-- int_meeting_todo
SET @col_td := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'int_meeting_todo'
      AND column_name = 'preset_type_code'
);
SET @sql_td := IF(@col_td = 0,
    'ALTER TABLE int_meeting_todo ADD COLUMN preset_type_code TINYINT NULL COMMENT ''1-5 模板会 6 自定义 NULL 未知'' AFTER meeting_id',
    'SELECT ''todo.preset_type_code exists'' AS _skip');
PREPARE stmt_td FROM @sql_td;
EXECUTE stmt_td;
DEALLOCATE PREPARE stmt_td;

-- int_meeting_minute
SET @col_mm := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'int_meeting_minute'
      AND column_name = 'preset_type_code'
);
SET @sql_mm := IF(@col_mm = 0,
    'ALTER TABLE int_meeting_minute ADD COLUMN preset_type_code TINYINT NULL COMMENT ''1-5 模板会 6 自定义 NULL 未知'' AFTER meeting_id',
    'SELECT ''minute.preset_type_code exists'' AS _skip');
PREPARE stmt_mm FROM @sql_mm;
EXECUTE stmt_mm;
DEALLOCATE PREPARE stmt_mm;

-- 历史数据回填
UPDATE int_meeting_participant p
    INNER JOIN int_meeting m ON p.meeting_id = m.id
SET p.preset_type_code = m.preset_type_code
WHERE p.preset_type_code IS NULL;

UPDATE int_transcript_segment s
    INNER JOIN int_meeting m ON s.meeting_id = m.id
SET s.preset_type_code = m.preset_type_code
WHERE s.preset_type_code IS NULL;

UPDATE int_meeting_todo t
    INNER JOIN int_meeting m ON t.meeting_id = m.id
SET t.preset_type_code = m.preset_type_code
WHERE t.preset_type_code IS NULL;

UPDATE int_meeting_minute mm
    INNER JOIN int_meeting m ON mm.meeting_id = m.id
SET mm.preset_type_code = m.preset_type_code
WHERE mm.preset_type_code IS NULL;

-- 索引（幂等：已存在则跳过）
SET @idx_pp := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'int_meeting_participant'
      AND index_name = 'idx_participant_preset'
);
SET @sql_idx_pp := IF(@idx_pp = 0,
    'ALTER TABLE int_meeting_participant ADD KEY idx_participant_preset (preset_type_code)',
    'SELECT ''idx_participant_preset exists'' AS _skip');
PREPARE stmt_idx_pp FROM @sql_idx_pp;
EXECUTE stmt_idx_pp;
DEALLOCATE PREPARE stmt_idx_pp;

SET @idx_ts := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'int_transcript_segment'
      AND index_name = 'idx_segment_preset'
);
SET @sql_idx_ts := IF(@idx_ts = 0,
    'ALTER TABLE int_transcript_segment ADD KEY idx_segment_preset (preset_type_code)',
    'SELECT ''idx_segment_preset exists'' AS _skip');
PREPARE stmt_idx_ts FROM @sql_idx_ts;
EXECUTE stmt_idx_ts;
DEALLOCATE PREPARE stmt_idx_ts;

SET @idx_td := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'int_meeting_todo'
      AND index_name = 'idx_todo_preset'
);
SET @sql_idx_td := IF(@idx_td = 0,
    'ALTER TABLE int_meeting_todo ADD KEY idx_todo_preset (preset_type_code)',
    'SELECT ''idx_todo_preset exists'' AS _skip');
PREPARE stmt_idx_td FROM @sql_idx_td;
EXECUTE stmt_idx_td;
DEALLOCATE PREPARE stmt_idx_td;

SET @idx_mm := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'int_meeting_minute'
      AND index_name = 'idx_minute_preset'
);
SET @sql_idx_mm := IF(@idx_mm = 0,
    'ALTER TABLE int_meeting_minute ADD KEY idx_minute_preset (preset_type_code)',
    'SELECT ''idx_minute_preset exists'' AS _skip');
PREPARE stmt_idx_mm FROM @sql_idx_mm;
EXECUTE stmt_idx_mm;
DEALLOCATE PREPARE stmt_idx_mm;

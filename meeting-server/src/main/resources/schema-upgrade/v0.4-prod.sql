-- ============================================================
-- 生产库 intelligence → schema.sql v0.4 结构升级（幂等片段，可分段执行）
-- 执行前请备份；建议在低峰期执行。
-- 探查基准：prod-schema-snapshot.json @ 2026-05-17
-- ============================================================

USE intelligence;

-- -----------------------------------------------------------------------------
-- 1. int_matter_progress_doc_config：数据修正
-- -----------------------------------------------------------------------------
UPDATE int_matter_progress_doc_config
SET config_name = 'preset1-comp-agenda-04'
WHERE id = 8 AND preset_type_code = 1 AND agenda_index = 4
  AND config_name = 'preset1-comp-agenda-03';

SET @col_token_mig := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'int_matter_progress_doc_config'
      AND column_name = 'feishu_doc_token'
);
SET @sql_token_mig := IF(@col_token_mig > 0,
    'UPDATE int_matter_progress_doc_config SET feishu_doc_url = CONCAT(''https://open.feishu.cn/docx/'', feishu_doc_token) WHERE (feishu_doc_url IS NULL OR TRIM(feishu_doc_url) = '''') AND feishu_doc_token IS NOT NULL AND TRIM(feishu_doc_token) <> ''''',
    'SELECT ''feishu_doc_token already dropped, skip url backfill'' AS _skip');
PREPARE stmt_token_mig FROM @sql_token_mig;
EXECUTE stmt_token_mig;
DEALLOCATE PREPARE stmt_token_mig;

-- -----------------------------------------------------------------------------
-- 2. int_matter_progress_doc_config：加列 resource_slot
-- -----------------------------------------------------------------------------
SET @col_rs := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'int_matter_progress_doc_config'
      AND column_name = 'resource_slot'
);
SET @sql_rs := IF(@col_rs = 0,
    'ALTER TABLE int_matter_progress_doc_config ADD COLUMN resource_slot INT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''同会序多份资料槽位 0,1,2…'' AFTER agenda_index',
    'SELECT ''resource_slot exists'' AS _skip');
PREPARE stmt_rs FROM @sql_rs;
EXECUTE stmt_rs;
DEALLOCATE PREPARE stmt_rs;

-- -----------------------------------------------------------------------------
-- 3. 索引：uk_preset_agenda_doc 含 resource_slot；补 config_name 唯一与查询索引
-- -----------------------------------------------------------------------------
SET @idx_old := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'int_matter_progress_doc_config'
      AND index_name = 'uk_preset_agenda_doc' AND seq_in_index = 3 AND column_name = 'resource_slot'
);
SET @sql_drop_uk := IF(@idx_old = 0,
    'ALTER TABLE int_matter_progress_doc_config DROP INDEX uk_preset_agenda_doc',
    'SELECT ''uk_preset_agenda_doc already v0.4'' AS _skip');
SET @has_uk := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'int_matter_progress_doc_config'
      AND index_name = 'uk_preset_agenda_doc'
);
SET @sql_drop_uk2 := IF(@has_uk > 0 AND @idx_old = 0, @sql_drop_uk, 'SELECT ''skip drop uk'' AS _skip');
PREPARE stmt_duk FROM @sql_drop_uk2;
EXECUTE stmt_duk;
DEALLOCATE PREPARE stmt_duk;

SET @has_uk_new := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'int_matter_progress_doc_config'
      AND index_name = 'uk_preset_agenda_doc'
);
SET @sql_add_uk := IF(@has_uk_new = 0,
    'ALTER TABLE int_matter_progress_doc_config ADD UNIQUE KEY uk_preset_agenda_doc (preset_type_code, agenda_index, resource_slot)',
    'SELECT ''uk_preset_agenda_doc exists'' AS _skip');
PREPARE stmt_auk FROM @sql_add_uk;
EXECUTE stmt_auk;
DEALLOCATE PREPARE stmt_auk;

SET @idx_cn := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'int_matter_progress_doc_config'
      AND index_name = 'uk_matter_progress_config_name'
);
SET @sql_cn := IF(@idx_cn = 0,
    'ALTER TABLE int_matter_progress_doc_config ADD UNIQUE KEY uk_matter_progress_config_name (config_name)',
    'SELECT ''uk_matter_progress_config_name exists'' AS _skip');
PREPARE stmt_cn FROM @sql_cn;
EXECUTE stmt_cn;
DEALLOCATE PREPARE stmt_cn;

SET @idx_ei := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'int_matter_progress_doc_config'
      AND index_name = 'idx_matter_progress_enabled_id'
);
SET @sql_ei := IF(@idx_ei = 0,
    'ALTER TABLE int_matter_progress_doc_config ADD KEY idx_matter_progress_enabled_id (enabled, id)',
    'SELECT ''idx_matter_progress_enabled_id exists'' AS _skip');
PREPARE stmt_ei FROM @sql_ei;
EXECUTE stmt_ei;
DEALLOCATE PREPARE stmt_ei;

SET @idx_pr := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'int_matter_progress_doc_config'
      AND index_name = 'idx_matter_progress_preset'
);
SET @sql_pr := IF(@idx_pr = 0,
    'ALTER TABLE int_matter_progress_doc_config ADD KEY idx_matter_progress_preset (preset_type_code, enabled, agenda_index)',
    'SELECT ''idx_matter_progress_preset exists'' AS _skip');
PREPARE stmt_pr FROM @sql_pr;
EXECUTE stmt_pr;
DEALLOCATE PREPARE stmt_pr;

-- -----------------------------------------------------------------------------
-- 4. 列类型对齐 v0.4；删除废弃列
-- -----------------------------------------------------------------------------
ALTER TABLE int_matter_progress_doc_config
    MODIFY COLUMN config_name VARCHAR(64) NOT NULL COMMENT '逻辑配置名，全局唯一',
    MODIFY COLUMN feishu_doc_url VARCHAR(2000) NULL COMMENT '飞书链接：/docx/、/wiki/、/base/?table=',
    MODIFY COLUMN enabled TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '0关闭 1启用',
    MODIFY COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键';

SET @col_token := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'int_matter_progress_doc_config'
      AND column_name = 'feishu_doc_token'
);
SET @sql_dt := IF(@col_token > 0,
    'ALTER TABLE int_matter_progress_doc_config DROP COLUMN feishu_doc_token',
    'SELECT ''feishu_doc_token already dropped'' AS _skip');
PREPARE stmt_dt FROM @sql_dt;
EXECUTE stmt_dt;
DEALLOCATE PREPARE stmt_dt;

SET @col_test := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'int_matter_progress_doc_config'
      AND column_name = 'test_document_text'
);
SET @sql_tt := IF(@col_test > 0,
    'ALTER TABLE int_matter_progress_doc_config DROP COLUMN test_document_text',
    'SELECT ''test_document_text already dropped'' AS _skip');
PREPARE stmt_tt FROM @sql_tt;
EXECUTE stmt_tt;
DEALLOCATE PREPARE stmt_tt;

ALTER TABLE int_matter_progress_doc_config
    COMMENT = '会序飞书资料配置（主持页外链与 OpenClaw 通报数据源）';

-- -----------------------------------------------------------------------------
-- 5. int_meeting：chat_id 扩长
-- -----------------------------------------------------------------------------
ALTER TABLE int_meeting
    MODIFY COLUMN chat_id VARCHAR(100) NULL COMMENT '飞书群聊ID';

-- -----------------------------------------------------------------------------
-- 6. 预设主持模板：已移至 schema-seed/v0.4-prod-optional-data.sql（会覆盖 host_agenda，勿随应用重启执行）
-- -----------------------------------------------------------------------------

-- 新增
本地路径
综合：
https://ovjde0k7vc1.feishu.cn/wiki/AWufwT0tWiLCRKkdmhIcek5JnIg?table=tblxP8EAtOOQq7mt&view=vewM1Y9Vem
https://ovjde0k7vc1.feishu.cn/base/GYoHbrmQYaPtflsUubDcjGyknGd?table=tblcukp9eKr3REi7&view=vewM1Y9Vem
物流:
https://ovjde0k7vc1.feishu.cn/wiki/SizPw7qVci8TDokeeSYcK377nSh
https://ovjde0k7vc1.feishu.cn/wiki/BO4Kwdv65izpo8knLdWcr2UZns2
法务：
https://ovjde0k7vc1.feishu.cn/wiki/MQ53wMRs6irSRdku71bcwRJNnNh?table=tblLAcSqR3Vgu4wb&view=vew3qfhSyY
https://ovjde0k7vc1.feishu.cn/base/SnsXbyQ1Qa57fCsI8mrcRAIbnve?table=tbl7viO4AJ4ebD0B&view=vew3qfhSyY
-- =============================================================================
-- v0.9 会前事项对比通报 — 示例种子数据
-- =============================================================================
-- 前置：必须先执行 schema-upgrade/v0.9-weekly-matter-comparison.sql
--
-- 本脚本做什么：
--   §1 将 preset=1 会序 1～4 既有 SOURCE 行标记 config_role='SOURCE'（合并议程飞书外链）
--   §2 插入 OUTPUT 行 preset1-weekly-report-out（agenda_index=1 → 主持页第二项「前期项目汇报」）
--   §3 插入定时 job preset1-comprehensive-weekly（每周一 10:00，读 4 份 SOURCE + 近 7 天纪要）
--
-- 设计约束（重要）：
--   - bot 只 UPDATE generated_report_url / generated_report_at，永不覆盖 feishu_doc_url
--
-- 幂等：可重复执行；已有 feishu_doc_url / generated_report_url 不会被 ON DUPLICATE 覆盖
--
-- 执行：
--   ProdSchemaMigrate --apply src/main/resources/schema-seed/v0.9-weekly-matter-comparison-seed.sql
--
-- 验证：
--   SELECT * FROM int_weekly_matter_comparison_job WHERE job_name='preset1-comprehensive-weekly';
--   POST feishu-scheduled-bot/api/weekly-comparison/jobs/{id}/execute
--
-- 文档：docs/weekly-matter-comparison.md
-- =============================================================================

-- -----------------------------------------------------------------------------
-- §1 SOURCE 行：preset=1 会序 1～4 飞书资料（与 schema-data.sql 中 config_name 对应）
--    PresetAgendaDocService 合并议程时只取 config_role=SOURCE|BOTH 且 enabled=1 的行
-- -----------------------------------------------------------------------------
UPDATE int_matter_progress_doc_config
SET config_role = 'SOURCE'
WHERE config_name IN (
    'preset1-comp-agenda-01',
    'preset1-comp-agenda-02',
    'preset1-comp-agenda-03',
    'preset1-comp-agenda-04'
  )
  AND (config_role IS NULL OR config_role = '' OR config_role = 'SOURCE');

-- -----------------------------------------------------------------------------
-- §2 OUTPUT 行：bot 写回 generated_report_url 的目标
--    preset_type_code=1, agenda_index=1 → 综合管理会 host_agenda 第 2 项
--    feishu_doc_url 可选：若需主持页同时展示「原始资料」外链，可 UPDATE 填入；NULL 则仅展示通报链接
-- -----------------------------------------------------------------------------
INSERT INTO int_matter_progress_doc_config (
    config_name, preset_type_code, agenda_index, resource_slot,
    feishu_doc_url, config_role, enabled
) VALUES (
    'preset1-weekly-report-out',
    1, 1, 0,
    NULL, 'OUTPUT', 1
)
ON DUPLICATE KEY UPDATE
    preset_type_code = VALUES(preset_type_code),
    agenda_index     = VALUES(agenda_index),
    config_role      = VALUES(config_role),
    enabled          = VALUES(enabled);
    -- 刻意不 UPDATE feishu_doc_url / generated_report_url，避免覆盖生产已有值

-- 扩展：若会序 2～4 各需独立通报，可取消注释并增加对应 job（或共用一个 OUTPUT + 多 agenda 需改业务）
-- INSERT INTO int_matter_progress_doc_config (config_name, preset_type_code, agenda_index, resource_slot, config_role, enabled)
-- VALUES
--     ('preset1-weekly-report-out-02', 1, 2, 0, 'OUTPUT', 1),
--     ('preset1-weekly-report-out-03', 1, 3, 0, 'OUTPUT', 1),
--     ('preset1-weekly-report-out-04', 1, 4, 0, 'OUTPUT', 1)
-- ON DUPLICATE KEY UPDATE config_role = VALUES(config_role), enabled = VALUES(enabled);

-- -----------------------------------------------------------------------------
-- §3 定时 job：feishu-scheduled-bot WeeklyComparisonScheduleService 同步到 Quartz
--    minute_query_type=PRESET_LAST_7_DAYS → 查 preset=1 近 7 天 status=READY 的 int_meeting_minute
--    feishu_folder_token：替换为云空间文件夹 token；NULL 则创建在应用默认目录
-- -----------------------------------------------------------------------------
INSERT INTO int_weekly_matter_comparison_job (
    job_name,
    enabled,
    cron_expression,
    schedule_timezone,
    source_config_names,
    minute_query_type,
    minute_query_params,
    output_config_name,
    output_doc_title_tpl,
    feishu_folder_token
) VALUES (
    'preset1-comprehensive-weekly',
    1,
    '0 10 * * MON',
    'Asia/Shanghai',
    JSON_ARRAY(
        'preset1-comp-agenda-01',
        'preset1-comp-agenda-02',
        'preset1-comp-agenda-03',
        'preset1-comp-agenda-04'
    ),
    'PRESET_LAST_7_DAYS',
    JSON_OBJECT('presetTypeCode', 1, 'days', 7),
    'preset1-weekly-report-out',
    '综合管理会事项对比通报-{date}',
    NULL
)
ON DUPLICATE KEY UPDATE
    enabled               = VALUES(enabled),
    cron_expression       = VALUES(cron_expression),
    schedule_timezone     = VALUES(schedule_timezone),
    source_config_names   = VALUES(source_config_names),
    minute_query_type     = VALUES(minute_query_type),
    minute_query_params   = VALUES(minute_query_params),
    output_config_name    = VALUES(output_config_name),
    output_doc_title_tpl  = VALUES(output_doc_title_tpl);
    -- feishu_folder_token：首次 NULL；生产可手工 UPDATE 一次

-- -----------------------------------------------------------------------------
-- §4 非模板会议示例（按 meeting_id 拉纪要）— 默认注释，按需启用并替换 UUID
-- -----------------------------------------------------------------------------
-- INSERT INTO int_matter_progress_doc_config (
--     config_name, preset_type_code, agenda_index, resource_slot, config_role, enabled
-- ) VALUES ('adhoc-weekly-report-out', NULL, 1, 0, 'OUTPUT', 1)
-- ON DUPLICATE KEY UPDATE config_role = VALUES(config_role), enabled = VALUES(enabled);
--
-- INSERT INTO int_weekly_matter_comparison_job (
--     job_name, enabled, cron_expression, schedule_timezone,
--     source_config_names, minute_query_type, minute_query_params,
--     output_config_name, output_doc_title_tpl
-- ) VALUES (
--     'adhoc-matter-weekly',
--     1,
--     '0 10 * * MON',
--     'Asia/Shanghai',
--     JSON_ARRAY('your-source-config-name'),
--     'MEETING_IDS',
--     JSON_OBJECT('meetingIds', JSON_ARRAY('meeting-uuid-1', 'meeting-uuid-2')),
--     'adhoc-weekly-report-out',
--     '事项对比通报-{date}'
-- )
-- ON DUPLICATE KEY UPDATE enabled = VALUES(enabled);

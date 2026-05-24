-- =============================================================================
-- v0.9 会前事项对比通报 — 完整 INSERT 示例（参考脚本，非自动执行）
-- =============================================================================
-- 用途：新环境从零配置，或对照字段含义手工改 URL / preset / meeting_id 后执行
-- 前置：schema-upgrade/v0.9-weekly-matter-comparison.sql 已执行
--
-- 表关系：
--   int_matter_progress_doc_config
--     SOURCE  → 会序合并飞书资料（feishu_doc_url）；bot 读取对比
--     OUTPUT  → bot 写回 generated_report_url；主持页只读展示
--     BOTH    → 同时作 SOURCE + 写回（少见）
--   int_weekly_matter_comparison_job
--     source_config_names → 指向 SOURCE/BOTH 的 config_name
--     output_config_name  → 指向 OUTPUT/BOTH 的 config_name
--
-- 替换占位符：
--   YOUR.feishu.cn          → 租户域名
--   docx/REPLACE_ME         → 飞书 docx / wiki / base 链接
--   fldcnXXXXXXXX           → 飞书云空间文件夹 token（可选）
--   meeting-uuid-1          → int_meeting.id（MEETING_IDS 模式）
--
-- 文档：docs/weekly-matter-comparison.md
-- =============================================================================


-- =============================================================================
-- 场景 A：综合管理会 preset=1 — 从零 INSERT（含 SOURCE + OUTPUT + Job）
-- agenda_index 为 0-based，与 host_agenda.items 下标一致
-- =============================================================================

-- -----------------------------------------------------------------------------
-- A1. SOURCE 行：4 个会序各一份飞书资料（会中议程左侧「参考资料」外链）
--     config_role 默认已是 SOURCE；显式写出便于阅读
-- -----------------------------------------------------------------------------
INSERT INTO int_matter_progress_doc_config (
    config_name,
    preset_type_code,
    agenda_index,
    resource_slot,
    feishu_doc_url,
    enabled,
    config_role
    -- generated_report_url / generated_report_at：仅 bot 写，INSERT 时不要填
) VALUES
    (
        'preset1-comp-agenda-01',
        1, 1, 0,
        'https://YOUR.feishu.cn/base/SnsXbyQ1Qa57fCsI8mrcRAIbnve?table=tbl7viO4AJ4ebD0B&view=vew3qfhSyY',
        1,
        'SOURCE'
    ),
    (
        'preset1-comp-agenda-02',
        1, 2, 0,
        'https://YOUR.feishu.cn/docx/CzrSd90yMoKnsoxR4xGcEI5Fncf',
        1,
        'SOURCE'
    ),
    (
        'preset1-comp-agenda-03',
        1, 3, 0,
        'https://YOUR.feishu.cn/wiki/BO4Kwdv65izpo8knLdWcr2UZns2',
        1,
        'SOURCE'
    ),
    (
        'preset1-comp-agenda-04',
        1, 4, 0,
        'https://YOUR.feishu.cn/base/GYoHbrmQYaPtflsUubDcJGyknGd?table=tblcukp9eKr3REI7&view=vewM1Y9Vem',
        1,
        'SOURCE'
    )
ON DUPLICATE KEY UPDATE
    preset_type_code = VALUES(preset_type_code),
    agenda_index     = VALUES(agenda_index),
    resource_slot    = VALUES(resource_slot),
    config_role      = VALUES(config_role),
    enabled          = VALUES(enabled);
    -- 生产已有 feishu_doc_url 时勿覆盖：去掉上面 feishu_doc_url 列或改用 seed 脚本的 UPDATE 方式

-- -----------------------------------------------------------------------------
-- A2. 同会序多份资料（resource_slot 0/1/2）— 可选
--     同一 agenda_index=1 绑定 base + docx 两份 SOURCE
-- -----------------------------------------------------------------------------
-- INSERT INTO int_matter_progress_doc_config (
--     config_name, preset_type_code, agenda_index, resource_slot,
--     feishu_doc_url, enabled, config_role
-- ) VALUES
--     ('preset1-agenda1-base', 1, 1, 0, 'https://YOUR.feishu.cn/base/...?table=tbl...', 1, 'SOURCE'),
--     ('preset1-agenda1-docx', 1, 1, 1, 'https://YOUR.feishu.cn/docx/...', 1, 'SOURCE')
-- ON DUPLICATE KEY UPDATE enabled = VALUES(enabled), config_role = VALUES(config_role);

-- -----------------------------------------------------------------------------
-- A3. OUTPUT 行：bot 写回对比通报链接的目标
--     preset=1, agenda_index=1 → 主持页进入第 2 项会序时展示 generatedReportUrl
--     feishu_doc_url 可 NULL（只展示 bot 生成的通报）；也可填「原始汇总 doc」供双链展示
-- -----------------------------------------------------------------------------
INSERT INTO int_matter_progress_doc_config (
    config_name,
    preset_type_code,
    agenda_index,
    resource_slot,
    feishu_doc_url,
    enabled,
    config_role
) VALUES (
    'preset1-weekly-report-out',
    1, 1, 0,
    NULL,
    1,
    'OUTPUT'
)
ON DUPLICATE KEY UPDATE
    preset_type_code = VALUES(preset_type_code),
    agenda_index     = VALUES(agenda_index),
    config_role      = VALUES(config_role),
    enabled          = VALUES(enabled);

-- -----------------------------------------------------------------------------
-- A4. Job：PRESET_LAST_7_DAYS — 读 preset=1 近 7 天 READY 纪要 + 4 份 SOURCE
--     每周一 10:00 Asia/Shanghai；feishu-scheduled-bot 同步到 Quartz
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
    -- last_run_* 由 bot 执行后自动写入，INSERT 时勿填
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
    'fldcnXXXXXXXX'
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
    -- feishu_folder_token 首次可 NULL；目录 token 未知时不要 ON DUPLICATE 覆盖


-- =============================================================================
-- 场景 B：会序 2～4 各独立 OUTPUT + 各一条 Job（扩展）
-- =============================================================================

-- INSERT INTO int_matter_progress_doc_config (
--     config_name, preset_type_code, agenda_index, resource_slot,
--     feishu_doc_url, enabled, config_role
-- ) VALUES
--     ('preset1-weekly-report-out-02', 1, 2, 0, NULL, 1, 'OUTPUT'),
--     ('preset1-weekly-report-out-03', 1, 3, 0, NULL, 1, 'OUTPUT'),
--     ('preset1-weekly-report-out-04', 1, 4, 0, NULL, 1, 'OUTPUT')
-- ON DUPLICATE KEY UPDATE config_role = VALUES(config_role), enabled = VALUES(enabled);

-- INSERT INTO int_weekly_matter_comparison_job (
--     job_name, enabled, cron_expression, schedule_timezone,
--     source_config_names, minute_query_type, minute_query_params,
--     output_config_name, output_doc_title_tpl, feishu_folder_token
-- ) VALUES (
--     'preset1-agenda2-weekly',
--     1, '0 10 * * MON', 'Asia/Shanghai',
--     JSON_ARRAY('preset1-comp-agenda-02'),
--     'PRESET_LAST_7_DAYS',
--     JSON_OBJECT('presetTypeCode', 1, 'days', 7),
--     'preset1-weekly-report-out-02',
--     '会序2事项对比通报-{date}',
--     NULL
-- );


-- =============================================================================
-- 场景 C：BOTH 行 — 同一 config 既合并议程又接收 bot 写回（少见）
-- =============================================================================

-- INSERT INTO int_matter_progress_doc_config (
--     config_name, preset_type_code, agenda_index, resource_slot,
--     feishu_doc_url, enabled, config_role
-- ) VALUES (
--     'preset2-agenda1-both',
--     2, 1, 0,
--     'https://YOUR.feishu.cn/docx/REPLACE_ME',
--     1,
--     'BOTH'
-- )
-- ON DUPLICATE KEY UPDATE config_role = VALUES(config_role), enabled = VALUES(enabled);

-- INSERT INTO int_weekly_matter_comparison_job (
--     job_name, enabled, cron_expression, schedule_timezone,
--     source_config_names, minute_query_type, minute_query_params,
--     output_config_name, output_doc_title_tpl
-- ) VALUES (
--     'preset2-weekly',
--     1, '0 15 * * MON', 'Asia/Shanghai',
--     JSON_ARRAY('preset2-agenda1-both'),
--     'PRESET_LAST_7_DAYS',
--     JSON_OBJECT('presetTypeCode', 2, 'days', 14),
--     'preset2-agenda1-both',
--     'preset2事项对比-{date}'
-- );


-- =============================================================================
-- 场景 D：非模板 / 指定 meeting_id — MEETING_IDS
-- preset_type_code=NULL 的 OUTPUT；纪要来自明确 meeting UUID 列表
-- =============================================================================

-- INSERT INTO int_matter_progress_doc_config (
--     config_name, preset_type_code, agenda_index, resource_slot,
--     feishu_doc_url, enabled, config_role
-- ) VALUES
--     ('adhoc-source-weekly', NULL, NULL, 0,
--      'https://YOUR.feishu.cn/docx/ADHOC_SOURCE_DOC', 1, 'SOURCE'),
--     ('adhoc-weekly-report-out', NULL, 1, 0,
--      NULL, 1, 'OUTPUT')
-- ON DUPLICATE KEY UPDATE config_role = VALUES(config_role), enabled = VALUES(enabled);

-- INSERT INTO int_weekly_matter_comparison_job (
--     job_name, enabled, cron_expression, schedule_timezone,
--     source_config_names, minute_query_type, minute_query_params,
--     output_config_name, output_doc_title_tpl, feishu_folder_token
-- ) VALUES (
--     'adhoc-matter-weekly',
--     1,
--     '0 10 * * MON',
--     'Asia/Shanghai',
--     JSON_ARRAY('adhoc-source-weekly'),
--     'MEETING_IDS',
--     JSON_OBJECT(
--         'meetingIds', JSON_ARRAY(
--             'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
--             'b2c3d4e5-f6a7-8901-bcde-f12345678901'
--         )
--     ),
--     'adhoc-weekly-report-out',
--     '专项事项对比通报-{date}',
--     NULL
-- );


-- =============================================================================
-- 场景 E：生产库已有 schema-data.sql SOURCE 行 — 仅补 v0.9 字段与 Job
-- （与 schema-seed/v0.9-weekly-matter-comparison-seed.sql 等价，便于对照）
-- =============================================================================

-- UPDATE int_matter_progress_doc_config
-- SET config_role = 'SOURCE'
-- WHERE config_name IN (
--     'preset1-comp-agenda-01', 'preset1-comp-agenda-02',
--     'preset1-comp-agenda-03', 'preset1-comp-agenda-04'
-- );

-- （然后执行上面 A3、A4 的 INSERT … ON DUPLICATE KEY UPDATE）


-- =============================================================================
-- 验收查询
-- =============================================================================

-- SELECT config_name, preset_type_code, agenda_index, config_role,
--        LEFT(feishu_doc_url, 60) AS doc_url,
--        LEFT(generated_report_url, 60) AS report_url,
--        generated_report_at, enabled
-- FROM int_matter_progress_doc_config
-- WHERE preset_type_code = 1 OR config_name LIKE 'preset1-%'
-- ORDER BY config_role, agenda_index;

-- SELECT id, job_name, enabled, cron_expression,
--        source_config_names, minute_query_type, minute_query_params,
--        output_config_name, last_run_status, last_run_at, last_run_error
-- FROM int_weekly_matter_comparison_job;

-- 手动试跑（替换 {id} 与 API Key）：
-- curl -sS -X POST "http://127.0.0.1:8764/api/weekly-comparison/jobs/1/execute" \
--   -H "X-API-Key: YOUR_API_KEY"

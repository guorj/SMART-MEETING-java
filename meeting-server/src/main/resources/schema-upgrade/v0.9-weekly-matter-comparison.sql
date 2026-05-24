-- =============================================================================
-- v0.9 会前事项对比通报 — 数据库结构升级
-- =============================================================================
-- 背景：
--   会中 OpenClaw 实时通报（AgendaBriefingService）已废弃。事项对比改由
--   feishu-scheduled-bot + matter-progress-core 在会前定时执行，结果写回本表。
--
-- 本脚本变更：
--   1) int_matter_progress_doc_config 增加 config_role / generated_report_url / generated_report_at
--   2) 新建 int_weekly_matter_comparison_job（Cron、SOURCE 列表、纪要查询、OUTPUT 写回目标）
--
-- 执行方式（幂等，可重复）：
--   ProdSchemaMigrate --apply src/main/resources/schema-upgrade/v0.9-weekly-matter-comparison.sql
--   或 mysql -h ... -u ... -p intelligence < src/main/resources/schema-upgrade/v0.9-weekly-matter-comparison.sql
--
-- 后续：执行 schema-seed/v0.9-weekly-matter-comparison-seed.sql 写入示例 SOURCE/OUTPUT/job
-- 文档：docs/weekly-matter-comparison.md
-- =============================================================================

SET @col_role := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'int_matter_progress_doc_config'
      AND column_name = 'config_role'
);
-- config_role：SOURCE=仅作议程资料合并；OUTPUT=仅 bot 写回通报链接；BOTH=两者兼有
SET @sql_role := IF(@col_role = 0,
    'ALTER TABLE int_matter_progress_doc_config ADD COLUMN config_role VARCHAR(16) NOT NULL DEFAULT ''SOURCE'' COMMENT ''SOURCE|OUTPUT|BOTH'' AFTER enabled',
    'SELECT ''config_role exists'' AS _skip');
PREPARE stmt_role FROM @sql_role;
EXECUTE stmt_role;
DEALLOCATE PREPARE stmt_role;

SET @col_gru := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'int_matter_progress_doc_config'
      AND column_name = 'generated_report_url'
);
-- generated_report_url：bot 成功执行后写入的飞书 Doc 只读链接；主持页展示；勿手工覆盖 SOURCE 的 feishu_doc_url
SET @sql_gru := IF(@col_gru = 0,
    'ALTER TABLE int_matter_progress_doc_config ADD COLUMN generated_report_url VARCHAR(2000) NULL COMMENT ''bot 写回的对比报告飞书 URL'' AFTER config_role',
    'SELECT ''generated_report_url exists'' AS _skip');
PREPARE stmt_gru FROM @sql_gru;
EXECUTE stmt_gru;
DEALLOCATE PREPARE stmt_gru;

SET @col_gra := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'int_matter_progress_doc_config'
      AND column_name = 'generated_report_at'
);
SET @sql_gra := IF(@col_gra = 0,
    'ALTER TABLE int_matter_progress_doc_config ADD COLUMN generated_report_at DATETIME NULL COMMENT ''最近一次 bot 生成时间'' AFTER generated_report_url',
    'SELECT ''generated_report_at exists'' AS _skip');
PREPARE stmt_gra FROM @sql_gra;
EXECUTE stmt_gra;
DEALLOCATE PREPARE stmt_gra;

-- int_weekly_matter_comparison_job：由 feishu-scheduled-bot 的 WeeklyComparisonScheduleService 同步到 Quartz
CREATE TABLE IF NOT EXISTS int_weekly_matter_comparison_job (
    id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    job_name              VARCHAR(64)     NOT NULL COMMENT '任务名，全局唯一，如 preset1-comprehensive-weekly',
    enabled               TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '0=关闭 1=启用；关闭后 Quartz 不触发',
    cron_expression       VARCHAR(64)     NOT NULL DEFAULT '0 10 * * MON' COMMENT 'Quartz cron，默认每周一 10:00',
    schedule_timezone     VARCHAR(64)     NOT NULL DEFAULT 'Asia/Shanghai' COMMENT 'Cron 解析时区',
    source_config_names   JSON            NOT NULL COMMENT 'SOURCE/BOTH 的 config_name 数组，读 feishu_doc_url',
    minute_query_type     VARCHAR(32)     NOT NULL COMMENT 'PRESET_LAST_7_DAYS | MEETING_IDS',
    minute_query_params   JSON            NOT NULL COMMENT '如 {"presetTypeCode":1,"days":7} 或 {"meetingIds":["uuid"]}',
    output_config_name    VARCHAR(64)     NOT NULL COMMENT '写回目标 config_name，须 OUTPUT 或 BOTH',
    output_doc_title_tpl  VARCHAR(200)    NOT NULL DEFAULT '事项对比通报-{date}' COMMENT '新建飞书 Doc 标题模板',
    feishu_folder_token   VARCHAR(128)    NULL COMMENT '可选：Doc 创建目录 token',
    last_run_at           DATETIME        NULL COMMENT '最近一次执行时间（成功或失败）',
    last_run_status       VARCHAR(20)     NULL COMMENT 'SUCCESS | FAILED',
    last_run_error        TEXT            NULL COMMENT '失败时错误摘要',
    created_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_weekly_comparison_job_name (job_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='周一事项对比定时任务（feishu-scheduled-bot 执行，不写 PushLog）';

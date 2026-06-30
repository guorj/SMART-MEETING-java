-- =============================================================================
-- v0.26 会前事项对比通报 — 结果入库（run + item 两表）
-- =============================================================================
-- 背景：
--   v0.9 起通报产物为飞书 Doc URL，写回 host_agenda.generatedReportUrl。
--   现改为入库 intelligence：每次执行产生 1 个 run 批次 + N 条 item（一行一事），
--   host_agenda 仅保存最新 generatedReportRunId 指针，历史保留。
--
-- 本脚本变更：
--   1) 新建 int_weekly_matter_comparison_run（运行批次头）
--   2) 新建 int_weekly_matter_comparison_item（事项明细，每行一个事项）
--   3) int_weekly_matter_comparison_job 增加 last_run_id 列
--
-- 执行方式（幂等，可重复）：
--   ProdSchemaMigrate --apply src/main/resources/schema-upgrade/v0.26-weekly-matter-comparison-report.sql
--   或 mysql -h ... -u ... -p intelligence < src/main/resources/schema-upgrade/v0.26-weekly-matter-comparison-report.sql
--
-- 文档：docs/weekly-matter-comparison.md · docs/weekly-matter-comparison-USER-MANUAL.md
-- =============================================================================

-- -----------------------------------------------------------------------------
-- §1 运行批次表 int_weekly_matter_comparison_run
--    每次 job 执行 INSERT 1 行；host_agenda.generatedReportRunId 指向最新 id
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS int_weekly_matter_comparison_run (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '运行批次主键；host_agenda.generatedReportRunId 指向此 id',
    job_id              BIGINT UNSIGNED NULL     COMMENT '关联 int_weekly_matter_comparison_job.id；手动补录可为 NULL',
    output_config_name  VARCHAR(64)     NOT NULL COMMENT '写回目标 docs.configName（OUTPUT/BOTH）',
    preset_type_code    TINYINT UNSIGNED NULL     COMMENT '冗余 preset code 1-5，便于按 preset 查询',
    agenda_index        INT UNSIGNED    NULL     COMMENT '冗余 host_agenda items 下标',
    title               VARCHAR(200)    NOT NULL COMMENT '通报标题（output_doc_title_tpl 渲染）',
    item_count          INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '本批次事项条数（入库后回填）',
    generation_status   VARCHAR(20)     NOT NULL DEFAULT 'READY' COMMENT 'READY|FAILED|PARTIAL（无事项或部分解析失败）',
    generated_at        DATETIME        NOT NULL COMMENT '本次生成时间（业务时间）',
    run_error           TEXT            NULL     COMMENT 'FAILED/PARTIAL 摘要；READY 为 NULL',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_wmc_run_output_time (output_config_name, generated_at DESC),
    INDEX idx_wmc_run_job (job_id),
    INDEX idx_wmc_run_preset_agenda (preset_type_code, agenda_index, generated_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='会前事项对比通报运行批次（每次执行一条，历史保留）';

-- -----------------------------------------------------------------------------
-- §2 事项明细表 int_weekly_matter_comparison_item
--    每行一个事项；与通报输出字段对齐（status_label/category/assignee/time_node）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS int_weekly_matter_comparison_item (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    run_id              BIGINT UNSIGNED NOT NULL COMMENT '所属批次 int_weekly_matter_comparison_run.id',
    category            VARCHAR(16)     NOT NULL COMMENT '分组：DELAYED|COMPLETED|IN_PROGRESS（延期/已完成/进行中）',
    matter_name         VARCHAR(500)    NOT NULL COMMENT '事项内容',
    assignee            VARCHAR(200)    NULL     COMMENT '责任人；缺失统一存 NULL（前端显示「未提及」），禁止存字符串',
    time_node           VARCHAR(200)    NULL     COMMENT '时间节点（截止/计划/完成时间，原文保留）',
    status_label        VARCHAR(32)     NOT NULL COMMENT '状态文案：延期|已完成|进行中（须与 category 一致）',
    sort_order          INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '组内排序（延期从重到轻、已完成降序、进行中升序）',
    source_config_name  VARCHAR(64)     NULL     COMMENT '可选：来源 SOURCE configName；多 SOURCE 共享 SQL 无法判断时存 NULL',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_wmc_item_run (run_id, category, sort_order),
    INDEX idx_wmc_item_run_category (run_id, category),
    CONSTRAINT fk_wmc_item_run FOREIGN KEY (run_id)
        REFERENCES int_weekly_matter_comparison_run (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='会前事项对比通报明细（一行一个事项）';

-- -----------------------------------------------------------------------------
-- §3 int_weekly_matter_comparison_job 增加 last_run_id
--    便于 Controller 响应与运维直接查最近一次 run，不必 JOIN run 表取 MAX
-- -----------------------------------------------------------------------------
SET @col_last_run_id := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'int_weekly_matter_comparison_job'
      AND column_name = 'last_run_id'
);
SET @sql_last_run_id := IF(@col_last_run_id = 0,
    'ALTER TABLE int_weekly_matter_comparison_job ADD COLUMN last_run_id BIGINT UNSIGNED NULL COMMENT ''最近一次成功 run id（指向 int_weekly_matter_comparison_run.id）'' AFTER last_run_status',
    'SELECT ''last_run_id exists'' AS _skip');
PREPARE stmt_last_run_id FROM @sql_last_run_id;
EXECUTE stmt_last_run_id;
DEALLOCATE PREPARE stmt_last_run_id;

-- -----------------------------------------------------------------------------
-- §4 会议纪要待办事项补录（run + item）
--    幂等策略：同一 output_config_name 下先删后插，只保留最新补录批次。
-- -----------------------------------------------------------------------------
SET @wmc_seed_generated_at := '2026-06-29 23:59:00';
SET @wmc_seed_output_config_name := 'preset1-weekly-report-out';
SET @wmc_seed_title := '会议纪要待办事项-2026-06-29';

DELETE FROM int_weekly_matter_comparison_run
WHERE output_config_name = @wmc_seed_output_config_name;

INSERT INTO int_weekly_matter_comparison_run (
    job_id,
    output_config_name,
    preset_type_code,
    agenda_index,
    title,
    item_count,
    generation_status,
    generated_at,
    run_error
) VALUES (
    NULL,
    @wmc_seed_output_config_name,
    NULL,
    NULL,
    @wmc_seed_title,
    25,
    'READY',
    @wmc_seed_generated_at,
    NULL
);

SET @wmc_seed_run_id := LAST_INSERT_ID();

INSERT INTO int_weekly_matter_comparison_item (
    run_id, category, matter_name, assignee, time_node, status_label, sort_order, source_config_name
) VALUES
    (@wmc_seed_run_id, 'COMPLETED', '科小本周提交申报资料', '付靖怡', '本周（完成）', '已完成', 1, NULL),
    (@wmc_seed_run_id, 'COMPLETED', '元包包灵活就业平台跑顺十几人', '管小慧', '已完成', '已完成', 2, NULL),
    (@wmc_seed_run_id, 'COMPLETED', '研发费和收入指标整理完毕可查看', '管小慧', '已完成', '已完成', 3, NULL),
    (@wmc_seed_run_id, 'COMPLETED', '搬家至莱西4车物品费用2720元', '管小慧', '2026-06-28（完成）', '已完成', 4, NULL),
    (@wmc_seed_run_id, 'COMPLETED', '高企26号下班前重新提交修改后资料', '付靖怡', '2026-06-26（完成）', '已完成', 5, NULL),
    (@wmc_seed_run_id, 'COMPLETED', '管理费最终版与珊珊汇报后各板块最终沟通', '管小慧', '2026-06-17（完成）', '已完成', 6, NULL);

INSERT INTO int_weekly_matter_comparison_item (
    run_id, category, matter_name, assignee, time_node, status_label, sort_order, source_config_name
) VALUES
    (@wmc_seed_run_id, 'IN_PROGRESS', '7月初沟通具身智能青岛训练基地', '管小慧', '2026-06-29（截止，剩余0天⚠️）', '进行中', 1, NULL),
    (@wmc_seed_run_id, 'IN_PROGRESS', '智能会议7月10日从飞书迁移至内部平台', '郭儒杰、管小慧', '2026-07-09（截止，剩余10天）', '进行中', 2, NULL),
    (@wmc_seed_run_id, 'IN_PROGRESS', '持续对接高端岗位猎聘', '管小慧', '2026-07-30（截止，剩余31天）', '进行中', 3, NULL),
    (@wmc_seed_run_id, 'IN_PROGRESS', '培训确定一级架构后本周内摸清人才集团机构及一汽服务能力', '管小慧', '2026-07-30（截止，剩余31天）', '进行中', 4, NULL),
    (@wmc_seed_run_id, 'IN_PROGRESS', '创屹开户延后标注为遗留项', '管小慧', '2026-08-30（截止，剩余62天）', '进行中', 5, NULL),
    (@wmc_seed_run_id, 'IN_PROGRESS', '逐人确认事项是否真的完成', '郭儒杰', '下次会前', '进行中', 6, NULL),
    (@wmc_seed_run_id, 'IN_PROGRESS', '查明OA立项已完成但仍显示的真实原因', '郭儒杰', '下次会前', '进行中', 7, NULL),
    (@wmc_seed_run_id, 'IN_PROGRESS', '确定斯利普合作意向后对接生产方案', '董秀红', '待启动', '进行中', 8, NULL),
    (@wmc_seed_run_id, 'IN_PROGRESS', '与何浩沟通管理范围调整（专注玉哲，池恒直接向郭儒杰汇报）', '郭儒杰', '推进中', '进行中', 9, NULL),
    (@wmc_seed_run_id, 'IN_PROGRESS', '清河历史往来账目核对及注销', '郭儒杰', '本周', '进行中', 10, NULL),
    (@wmc_seed_run_id, 'IN_PROGRESS', '集型设计回款资料审核', '郭儒杰', '本周', '进行中', 11, NULL),
    (@wmc_seed_run_id, 'IN_PROGRESS', '数据验证工作·两周内验证系统指标', '郭儒杰', '两周', '进行中', 12, NULL),
    (@wmc_seed_run_id, 'IN_PROGRESS', '集团管理费改为进行中，待确认后关闭', '管小慧', '待确认', '进行中', 13, NULL),
    (@wmc_seed_run_id, 'IN_PROGRESS', '吉林基地6月底前完成装修和办公布置', '管小慧', '6月底', '进行中', 14, NULL),
    (@wmc_seed_run_id, 'IN_PROGRESS', '与郝贺总沟通长春合作事宜', '管小慧', '推进中', '进行中', 15, NULL),
    (@wmc_seed_run_id, 'IN_PROGRESS', '与汽开科技局对接入驻企业介绍及业务规划', '管小慧', '推进中', '进行中', 16, NULL),
    (@wmc_seed_run_id, 'IN_PROGRESS', '清河注销核对历史往来账目后推进', '付靖怡', '推进中', '进行中', 17, NULL),
    (@wmc_seed_run_id, 'IN_PROGRESS', '山东创信税务重新开通预计7月完成', '付靖怡', '7月', '进行中', 18, NULL),
    (@wmc_seed_run_id, 'IN_PROGRESS', '科技股权融资未通过原因组织讨论', '付靖怡', '近期', '进行中', 19, NULL);


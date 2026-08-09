-- =============================================================================
-- 会前事项对比通报 — 取最新一批（run_id 最大）所有事项明细
-- 源表：intelligence.int_weekly_matter_comparison_item
--       intelligence.int_weekly_matter_comparison_run
-- 语义：每次执行动态取 run_id 最大的那批，列出该批全部 item（一行一事）
-- 用途：手工核对 / 主持页备用 SQL / 给前端或下游取最新一批明细
-- 执行：mysql -h ... -u ... -p intelligence < weekly-matter-comparison-latest-run-items.sql
-- 幂等：纯 SELECT，可重复执行
-- =============================================================================

-- -----------------------------------------------------------------------------
-- §1 全局最新一批：取 run_id 最大的那批的全部 item
--    不区分 output_config_name / preset，按 run.id DESC 取头一条
-- -----------------------------------------------------------------------------
SELECT
    i.id,
    i.run_id,
    r.title          AS run_title,
    r.output_config_name,
    r.preset_type_code,
    r.generated_at   AS run_generated_at,
    i.category,
    i.matter_name,
    COALESCE(NULLIF(TRIM(i.assignee), ''), '未提及') AS assignee,
    COALESCE(NULLIF(TRIM(i.time_node), ''), '—')    AS time_node,
    i.status_label,
    i.sort_order,
    i.source_config_name,
    i.created_at
FROM intelligence.int_weekly_matter_comparison_item i
INNER JOIN intelligence.int_weekly_matter_comparison_run r
    ON r.id = i.run_id
INNER JOIN (
    SELECT id
    FROM intelligence.int_weekly_matter_comparison_run
    ORDER BY id DESC
    LIMIT 1
) latest ON latest.id = i.run_id
ORDER BY
    FIELD(i.category, 'DELAYED', 'COMPLETED', 'IN_PROGRESS'),
    i.sort_order,
    i.id;

-- -----------------------------------------------------------------------------
-- §2 等价写法（子查询 MAX(run_id)）：与 §1 结果一致，便于嵌入到 UPDATE/INSERT
--    适合需要把"最新 run_id"作为一个标量复用的场景
-- -----------------------------------------------------------------------------
-- SET @latest_run_id := (
--     SELECT MAX(id)
--     FROM intelligence.int_weekly_matter_comparison_run
-- );
--
-- SELECT
--     i.id, i.run_id, i.category, i.matter_name,
--     COALESCE(NULLIF(TRIM(i.assignee), ''), '未提及') AS assignee,
--     COALESCE(NULLIF(TRIM(i.time_node), ''), '—')    AS time_node,
--     i.status_label, i.sort_order, i.source_config_name
-- FROM intelligence.int_weekly_matter_comparison_item i
-- WHERE i.run_id = @latest_run_id
-- ORDER BY
--     FIELD(i.category, 'DELAYED', 'COMPLETED', 'IN_PROGRESS'),
--     i.sort_order, i.id;

-- -----------------------------------------------------------------------------
-- §3 按 output_config_name 取各自最新一批（每个输出配置一行最新 run）
--    适合多 preset / 多输出配置并存、需要分别看"每个输出最新一批"的场景
-- -----------------------------------------------------------------------------
-- SELECT
--     i.id, i.run_id, r.output_config_name, r.title,
--     i.category, i.matter_name,
--     COALESCE(NULLIF(TRIM(i.assignee), ''), '未提及') AS assignee,
--     COALESCE(NULLIF(TRIM(i.time_node), ''), '—')    AS time_node,
--     i.status_label, i.sort_order
-- FROM intelligence.int_weekly_matter_comparison_item i
-- INNER JOIN intelligence.int_weekly_matter_comparison_run r
--     ON r.id = i.run_id
-- INNER JOIN (
--     SELECT output_config_name, MAX(id) AS max_run_id
--     FROM intelligence.int_weekly_matter_comparison_run
--     GROUP BY output_config_name
-- ) latest ON latest.max_run_id = i.run_id
-- ORDER BY
--     r.output_config_name,
--     FIELD(i.category, 'DELAYED', 'COMPLETED', 'IN_PROGRESS'),
--     i.sort_order, i.id;

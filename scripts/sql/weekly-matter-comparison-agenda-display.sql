-- 会序一 oabpTaskSql：会前事项对比通报（表在 oabp 库）
-- 粘贴到 preset / host_agenda 的 oabpTaskSql（单条 SELECT、末尾无分号）
-- 绑定：docs.configName = preset1-weekly-report-out 须与 run.output_config_name 一致
-- 表头：列别名即主持页表头（待办事项/责任人/时间节点/状态）
-- 排序：延期 → 已完成 → 进行中；组内时间由近及远
--
-- 重要：仅配 oabpTaskSql 时主持页默认不拉取资料（需同会序另有飞书资料或 OUTPUT.generatedReportRunId）
-- 见文档注释末尾「前端展示条件」

SELECT
  i.matter_name AS `待办事项`,
  COALESCE(NULLIF(TRIM(i.assignee), ''), '未提及') AS `责任人`,
  COALESCE(NULLIF(TRIM(i.time_node), ''), '—') AS `时间节点`,
  CASE i.status_label
    WHEN '延期' THEN '已延期'
    ELSE i.status_label
  END AS `状态`
FROM int_weekly_matter_comparison_item i
INNER JOIN int_weekly_matter_comparison_run r
  ON r.id = i.run_id
INNER JOIN (
  SELECT id
  FROM int_weekly_matter_comparison_run
  WHERE output_config_name = 'preset1-weekly-report-out'
  ORDER BY generated_at DESC, id DESC
  LIMIT 1
) latest ON latest.id = r.id
ORDER BY
  FIELD(i.category, 'DELAYED', 'COMPLETED', 'IN_PROGRESS'),
  CASE
    WHEN i.category = 'COMPLETED' THEN COALESCE(
      CAST(REGEXP_SUBSTR(i.time_node, '[0-9]{4}-[0-9]{2}-[0-9]{2}') AS DATE),
      DATE('1970-01-01')
    )
  END DESC,
  CASE
    WHEN i.category IN ('DELAYED', 'IN_PROGRESS') THEN COALESCE(
      CAST(REGEXP_SUBSTR(i.time_node, '[0-9]{4}-[0-9]{2}-[0-9]{2}') AS DATE),
      DATE('9999-12-31')
    )
  END ASC,
  i.sort_order,
  i.id

-- 前端展示条件（不改代码时的配置 workaround，二选一）：
-- A) 会序一 OUTPUT 资料 preset1-weekly-report-out 填写 generatedReportRunId = 最新 run.id
--    SELECT id FROM int_weekly_matter_comparison_run
--    WHERE output_config_name = 'preset1-weekly-report-out'
--    ORDER BY generated_at DESC LIMIT 1;
-- B) 会序一另绑一条飞书 SOURCE 资料（任意有效链接），触发 agenda-doc-content 拉取（同响应含 oabp 表格）

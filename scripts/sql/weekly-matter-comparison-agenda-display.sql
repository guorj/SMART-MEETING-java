-- 会序 oabpTaskSql / 主持页：会前事项对比通报（备用路径，不推荐）
-- 粘贴到 preset host_agenda.oabpTaskSql（单条 SELECT、末尾无分号）
-- 表在 intelligence 库；oabp 数据源默认 oabp_pro，必须写 intelligence. 前缀
-- oabp 账号需：GRANT SELECT ON intelligence.int_weekly_matter_comparison_* TO 'oabp'@'%';
--
-- ★ 推荐用方案一：OUTPUT docs 填 generatedReportRunId，勿配本 SQL（走 Java API 三组列表）
-- 若坚持 oabpTaskSql：取 output_config_name 下最新一批（不限周，避免空表）
-- 手跑 SQL 在 intelligence 有数据、主持页空表 → 多半是缺 intelligence. 前缀或本周筛选无匹配

SELECT
  i.matter_name AS `待办事项`,
  COALESCE(NULLIF(TRIM(i.assignee), ''), '未提及') AS `责任人`,
  COALESCE(NULLIF(TRIM(i.time_node), ''), '—') AS `时间节点`,
  CASE i.status_label
    WHEN '延期' THEN '已延期'
    ELSE i.status_label
  END AS `状态`
FROM intelligence.int_weekly_matter_comparison_item i
INNER JOIN intelligence.int_weekly_matter_comparison_run r
  ON r.id = i.run_id
INNER JOIN (
  SELECT id
  FROM intelligence.int_weekly_matter_comparison_run
  WHERE output_config_name = 'preset1-weekly-report-out'
  ORDER BY generated_at DESC, id DESC
  LIMIT 1
) latest ON latest.id = r.id
ORDER BY
  FIELD(i.category, 'DELAYED', 'COMPLETED', 'IN_PROGRESS'),
  i.sort_order,
  i.id

-- 仅本周（周一～周日）最新一批；本周尚无 run 时结果为空，主持页会只显示表头：
-- INNER JOIN (
--   SELECT id FROM intelligence.int_weekly_matter_comparison_run
--   WHERE output_config_name = 'preset1-weekly-report-out'
--     AND DATE(generated_at) >= DATE_SUB(CURDATE(), INTERVAL WEEKDAY(CURDATE()) DAY)
--     AND DATE(generated_at) < DATE_ADD(DATE_SUB(CURDATE(), INTERVAL WEEKDAY(CURDATE()) DAY), INTERVAL 7 DAY)
--   ORDER BY generated_at DESC, id DESC LIMIT 1
-- ) week_latest ON week_latest.id = r.id

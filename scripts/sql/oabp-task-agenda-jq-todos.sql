-- 会序 oabpTaskSql：综合管理待办（飞书多维表格 → jq_todos_* 三表）
-- 用途：粘贴到 preset / 会议 host_agenda JSON 的 oabpTaskSql 字段
-- 库：oabp_pro（meeting.datasource.external.oabp 默认库；MCP meeting-mysql 须跨库 SELECT）
-- 一行一条主任务；执行人合并；表头中文；组序 延期→已完成→进行中→未开始
-- 组内：已完成按截止日期倒序；进行中按剩余时间升序（截止日越近越前，无截止日排后）
-- 表名统一 oabp_pro. 前缀，兼容 MCP 默认库 intelligence 与 meeting-server oabp 直连
-- 按执行人筛选见 oabp-task-agenda-jq-todos-guanxiaohui.sql / oabp-task-agenda-jq-todos-fujingyi.sql

SELECT
  t.task_name AS `待办事项`,
  t.business_block AS `项目类别`,
  GROUP_CONCAT(DISTINCT u.nickname ORDER BY s.id SEPARATOR '、') AS `执行人`,
  t.progress AS `进度`,
  CASE t.status
    WHEN 0 THEN '未开始'
    WHEN 1 THEN '进行中'
    WHEN 2 THEN '已完成'
    WHEN 3 THEN '已延期'
  END AS `状态`,
  t.start_date AS `开始日期`,
  t.planned_end_date AS `截止日期`,
  NULLIF(TRIM(t.task_detail), '') AS `长期任务`
FROM oabp_pro.jq_todos_task t
LEFT JOIN oabp_pro.jq_todos_subtask s
  ON s.parent_id = t.id
  AND (s.deleted = 0 OR s.deleted IS NULL)
LEFT JOIN oabp_pro.system_users u
  ON u.id = s.asignee_id
  AND (u.deleted = 0 OR u.deleted IS NULL)
LEFT JOIN oabp_pro.jq_todos_task_followup f
  ON f.task_id = t.id
  AND f.task_type = 0
  AND (f.deleted = 0 OR f.deleted IS NULL)
  AND f.id = (
    SELECT MAX(f2.id)
    FROM oabp_pro.jq_todos_task_followup f2
    WHERE f2.task_id = t.id
      AND f2.task_type = 0
      AND (f2.deleted = 0 OR f2.deleted IS NULL)
  )
WHERE (t.deleted = 0 OR t.deleted IS NULL)
  AND t.remark LIKE '[feishu:recId=%'
GROUP BY
  t.id,
  t.task_name,
  t.business_block,
  t.progress,
  t.status,
  t.start_date,
  t.planned_end_date,
  t.task_detail
ORDER BY
  CASE t.status
    WHEN 3 THEN 0
    WHEN 2 THEN 1
    WHEN 1 THEN 2
    WHEN 0 THEN 3
    ELSE 4
  END,
  CASE WHEN t.status = 3 THEN t.planned_end_date END ASC,
  CASE WHEN t.status = 2 THEN t.planned_end_date END DESC,
  CASE WHEN t.status = 1 THEN CASE WHEN t.planned_end_date IS NULL THEN 1 ELSE 0 END END,
  CASE WHEN t.status = 1 THEN t.planned_end_date END ASC,
  CASE WHEN t.status = 0 THEN CASE WHEN t.planned_end_date IS NULL THEN 1 ELSE 0 END END,
  CASE WHEN t.status = 0 THEN t.planned_end_date END ASC,
  t.id

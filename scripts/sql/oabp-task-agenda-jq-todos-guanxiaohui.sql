-- 会序 oabpTaskSql：管小慧汇报（子任务粒度）
-- 用途：粘贴到会序「管小慧汇报」的 oabpTaskSql（如 preset1-comp-agenda-02）
-- 一行一条管小慧子任务；待办事项取自 jq_todos_subtask；状态/日期/进度取自主任务；负责人固定「管小慧」

SELECT
  COALESCE(NULLIF(TRIM(s.task_name), ''), t.task_name) AS `待办事项`,
  t.business_block AS `项目类别`,
  '管小慧' AS `执行人`,
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
FROM oabp_pro.jq_todos_subtask s
INNER JOIN oabp_pro.jq_todos_task t
  ON t.id = s.parent_id
  AND (t.deleted = 0 OR t.deleted IS NULL)
INNER JOIN oabp_pro.system_users u
  ON u.id = s.asignee_id
  AND (u.deleted = 0 OR u.deleted IS NULL)
  AND u.nickname LIKE '%管小慧%'
LEFT JOIN oabp_pro.jq_todos_task_followup f
  ON f.task_id = s.id
  AND f.task_type = 1
  AND (f.deleted = 0 OR f.deleted IS NULL)
  AND f.id = (
    SELECT MAX(f2.id)
    FROM oabp_pro.jq_todos_task_followup f2
    WHERE f2.task_id = s.id
      AND f2.task_type = 1
      AND (f2.deleted = 0 OR f2.deleted IS NULL)
  )
WHERE (s.deleted = 0 OR s.deleted IS NULL)
  AND t.remark LIKE '[feishu:recId=%'
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
  s.id

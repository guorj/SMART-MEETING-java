-- =============================================================================
-- 会前通报延期事项 → oabp_pro.jq_todos_* 同步
-- 源：intelligence.int_weekly_matter_comparison_item（run_id=2, status_label=延期）
-- 目标：oabp_pro.jq_todos_task + jq_todos_subtask
-- 幂等：主任务 remark=[wmc:runId=2:itemId={id}]；子任务 remark=...:sub=1
-- 执行：mysql -h ... -u ... -p < weekly-matter-comparison-to-jq-todos-run2-delayed.sql
-- 前置：同一 MySQL 实例可跨库读写 intelligence / oabp_pro
-- 注意：intelligence(utf8mb4_unicode_ci) 与 oabp_pro(utf8mb4_0900_ai_ci) 跨库比较须统一 COLLATE
-- =============================================================================

SET @wmc_run_id := 2;

-- -----------------------------------------------------------------------------
-- §0 预览源数据
-- -----------------------------------------------------------------------------
SELECT
  i.id,
  i.matter_name,
  i.assignee,
  i.time_node,
  i.status_label,
  i.category
FROM intelligence.int_weekly_matter_comparison_item i
WHERE i.run_id = @wmc_run_id
  AND i.status_label COLLATE utf8mb4_unicode_ci = '延期'
ORDER BY i.sort_order, i.id;

-- -----------------------------------------------------------------------------
-- §1 已有主任务：按事项名匹配，置 status=3（已延期）
-- -----------------------------------------------------------------------------
UPDATE oabp_pro.jq_todos_task t
INNER JOIN intelligence.int_weekly_matter_comparison_item i
  ON TRIM(t.task_name) COLLATE utf8mb4_unicode_ci = TRIM(i.matter_name) COLLATE utf8mb4_unicode_ci
SET
  t.status = 3,
  t.progress = CASE WHEN t.progress >= 100 THEN 99 ELSE t.progress END,
  t.planned_end_date = COALESCE(
    STR_TO_DATE(REGEXP_SUBSTR(i.time_node, '[0-9]{4}-[0-9]{2}-[0-9]{2}'), '%Y-%m-%d'),
    t.planned_end_date,
    DATE_SUB(CURDATE(), INTERVAL 1 DAY)
  ),
  t.remark = CASE
    WHEN t.remark COLLATE utf8mb4_unicode_ci
      LIKE CONCAT('[wmc:runId=', @wmc_run_id, ':itemId=%') COLLATE utf8mb4_unicode_ci
      THEN t.remark
    ELSE CONCAT('[wmc:runId=', @wmc_run_id, ':itemId=', i.id, ']')
  END,
  t.task_detail = NULLIF(
    TRIM(CONCAT_WS('；',
      NULLIF(TRIM(t.task_detail), ''),
      CONCAT('会前通报：', COALESCE(NULLIF(TRIM(i.time_node), ''), '已延期'))
    )),
    ''
  ),
  t.update_time = NOW()
WHERE i.run_id = @wmc_run_id
  AND i.status_label COLLATE utf8mb4_unicode_ci = '延期'
  AND (t.deleted = 0 OR t.deleted IS NULL);

-- -----------------------------------------------------------------------------
-- §2 未匹配主任务：INSERT jq_todos_task
-- -----------------------------------------------------------------------------
INSERT INTO oabp_pro.jq_todos_task (
  task_name,
  business_block,
  project_id,
  decision_maker_user_id,
  progress,
  start_date,
  planned_end_date,
  status,
  remark,
  task_detail,
  creator,
  create_time,
  updater,
  update_time,
  deleted,
  tenant_id
)
SELECT
  i.matter_name AS task_name,
  '未分类' AS business_block,
  NULL AS project_id,
  0 AS decision_maker_user_id,
  0 AS progress,
  COALESCE(
    STR_TO_DATE(REGEXP_SUBSTR(i.time_node, '[0-9]{4}-[0-9]{2}-[0-9]{2}'), '%Y-%m-%d'),
    DATE_SUB(CURDATE(), INTERVAL 30 DAY)
  ) AS start_date,
  COALESCE(
    STR_TO_DATE(REGEXP_SUBSTR(i.time_node, '[0-9]{4}-[0-9]{2}-[0-9]{2}'), '%Y-%m-%d'),
    DATE_SUB(CURDATE(), INTERVAL 1 DAY)
  ) AS planned_end_date,
  3 AS status,
  CONCAT('[wmc:runId=', @wmc_run_id, ':itemId=', i.id, ']') AS remark,
  CONCAT_WS('；',
    CONCAT('会前通报：', COALESCE(NULLIF(TRIM(i.time_node), ''), '已延期')),
    CASE
      WHEN NULLIF(TRIM(i.assignee), '') IS NOT NULL
        THEN CONCAT('责任人：', TRIM(i.assignee))
    END
  ) AS task_detail,
  'wmc-sync' AS creator,
  NOW() AS create_time,
  'wmc-sync' AS updater,
  NOW() AS update_time,
  0 AS deleted,
  0 AS tenant_id
FROM intelligence.int_weekly_matter_comparison_item i
WHERE i.run_id = @wmc_run_id
  AND i.status_label COLLATE utf8mb4_unicode_ci = '延期'
  AND NOT EXISTS (
    SELECT 1
    FROM oabp_pro.jq_todos_task t
    WHERE (t.deleted = 0 OR t.deleted IS NULL)
      AND (
        TRIM(t.task_name) COLLATE utf8mb4_unicode_ci = TRIM(i.matter_name) COLLATE utf8mb4_unicode_ci
        OR t.remark COLLATE utf8mb4_unicode_ci
          = CONCAT('[wmc:runId=', @wmc_run_id, ':itemId=', i.id, ']') COLLATE utf8mb4_unicode_ci
      )
  );

-- -----------------------------------------------------------------------------
-- §3 子任务：同步第一位责任人 → jq_todos_subtask.asignee_id
-- -----------------------------------------------------------------------------
INSERT INTO oabp_pro.jq_todos_subtask (
  parent_id,
  task_name,
  asignee_id,
  remark,
  creator,
  updater,
  create_time,
  update_time,
  deleted,
  tenant_id
)
SELECT
  t.id AS parent_id,
  t.task_name,
  COALESCE(u.id, 0) AS asignee_id,
  CONCAT('[wmc:runId=', @wmc_run_id, ':itemId=', i.id, ':sub=1]') AS remark,
  NULL AS creator,
  NULL AS updater,
  NOW() AS create_time,
  NOW() AS update_time,
  0 AS deleted,
  0 AS tenant_id
FROM intelligence.int_weekly_matter_comparison_item i
INNER JOIN oabp_pro.jq_todos_task t
  ON (
    t.remark COLLATE utf8mb4_unicode_ci
      = CONCAT('[wmc:runId=', @wmc_run_id, ':itemId=', i.id, ']') COLLATE utf8mb4_unicode_ci
    OR TRIM(t.task_name) COLLATE utf8mb4_unicode_ci = TRIM(i.matter_name) COLLATE utf8mb4_unicode_ci
  )
  AND (t.deleted = 0 OR t.deleted IS NULL)
LEFT JOIN oabp_pro.system_users u
  ON (
    u.nickname COLLATE utf8mb4_unicode_ci = TRIM(
      SUBSTRING_INDEX(
        SUBSTRING_INDEX(
          REPLACE(REPLACE(REPLACE(COALESCE(i.assignee, ''), '（', '('), '）', ')'), '、', ','),
          '(',
          1
        ),
        ',',
        1
      )
    ) COLLATE utf8mb4_unicode_ci
    OR u.nickname COLLATE utf8mb4_unicode_ci LIKE CONCAT(
      '%',
      TRIM(
        SUBSTRING_INDEX(
          SUBSTRING_INDEX(
            REPLACE(REPLACE(REPLACE(COALESCE(i.assignee, ''), '（', '('), '）', ')'), '、', ','),
            '(',
            1
          ),
          ',',
          1
        )
      ),
      '%'
    ) COLLATE utf8mb4_unicode_ci
  )
  AND (u.deleted = 0 OR u.deleted IS NULL)
WHERE i.run_id = @wmc_run_id
  AND i.status_label COLLATE utf8mb4_unicode_ci = '延期'
  AND NOT EXISTS (
    SELECT 1
    FROM oabp_pro.jq_todos_subtask s
    WHERE s.parent_id = t.id
      AND s.remark COLLATE utf8mb4_unicode_ci
        = CONCAT('[wmc:runId=', @wmc_run_id, ':itemId=', i.id, ':sub=1]') COLLATE utf8mb4_unicode_ci
      AND (s.deleted = 0 OR s.deleted IS NULL)
  );

UPDATE oabp_pro.jq_todos_subtask s
INNER JOIN oabp_pro.jq_todos_task t
  ON t.id = s.parent_id
  AND (t.deleted = 0 OR t.deleted IS NULL)
INNER JOIN intelligence.int_weekly_matter_comparison_item i
  ON s.remark COLLATE utf8mb4_unicode_ci
    = CONCAT('[wmc:runId=', @wmc_run_id, ':itemId=', i.id, ':sub=1]') COLLATE utf8mb4_unicode_ci
LEFT JOIN oabp_pro.system_users u
  ON (
    u.nickname COLLATE utf8mb4_unicode_ci = TRIM(
      SUBSTRING_INDEX(
        SUBSTRING_INDEX(
          REPLACE(REPLACE(REPLACE(COALESCE(i.assignee, ''), '（', '('), '）', ')'), '、', ','),
          '(',
          1
        ),
        ',',
        1
      )
    ) COLLATE utf8mb4_unicode_ci
    OR u.nickname COLLATE utf8mb4_unicode_ci LIKE CONCAT(
      '%',
      TRIM(
        SUBSTRING_INDEX(
          SUBSTRING_INDEX(
            REPLACE(REPLACE(REPLACE(COALESCE(i.assignee, ''), '（', '('), '）', ')'), '、', ','),
            '(',
            1
          ),
          ',',
          1
        )
      ),
      '%'
    ) COLLATE utf8mb4_unicode_ci
  )
  AND (u.deleted = 0 OR u.deleted IS NULL)
SET
  s.asignee_id = COALESCE(u.id, 0),
  s.task_name = t.task_name,
  s.update_time = NOW()
WHERE i.run_id = @wmc_run_id
  AND i.status_label COLLATE utf8mb4_unicode_ci = '延期'
  AND (s.deleted = 0 OR s.deleted IS NULL);

-- -----------------------------------------------------------------------------
-- §4 验收
-- -----------------------------------------------------------------------------
SELECT
  i.id AS item_id,
  i.matter_name,
  i.assignee,
  t.id AS task_id,
  t.status,
  t.remark,
  s.id AS subtask_id,
  s.asignee_id,
  u.nickname AS assignee_name
FROM intelligence.int_weekly_matter_comparison_item i
LEFT JOIN oabp_pro.jq_todos_task t
  ON (
    t.remark COLLATE utf8mb4_unicode_ci
      = CONCAT('[wmc:runId=', @wmc_run_id, ':itemId=', i.id, ']') COLLATE utf8mb4_unicode_ci
    OR TRIM(t.task_name) COLLATE utf8mb4_unicode_ci = TRIM(i.matter_name) COLLATE utf8mb4_unicode_ci
  )
  AND (t.deleted = 0 OR t.deleted IS NULL)
LEFT JOIN oabp_pro.jq_todos_subtask s
  ON s.parent_id = t.id
  AND s.remark COLLATE utf8mb4_unicode_ci
    = CONCAT('[wmc:runId=', @wmc_run_id, ':itemId=', i.id, ':sub=1]') COLLATE utf8mb4_unicode_ci
  AND (s.deleted = 0 OR s.deleted IS NULL)
LEFT JOIN oabp_pro.system_users u
  ON u.id = s.asignee_id
WHERE i.run_id = @wmc_run_id
  AND i.status_label COLLATE utf8mb4_unicode_ci = '延期'
ORDER BY i.sort_order, i.id;

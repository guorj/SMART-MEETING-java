-- =============================================================================
-- v0.27 待办二段式裁决 — int_meeting_todo 增加决策人字段
-- =============================================================================
-- 背景：
--   原待办完成流程：责任人点"完成"直接置 COMPLETED，无中间裁决。
--   新增二段式裁决：责任人提交完成 → 查 OABP jq_todos_task.decision_maker_user_id
--   → 有决策人则置 PENDING_DECISION 并推送裁决卡 → 决策人裁决完成/延期/驳回。
--   决策人字段缓存在 int_meeting_todo，避免每次回调重查 OABP 库。
--
-- 本脚本变更（全部幂等，可重复执行）：
--   1) int_meeting_todo 新增 6 列：决策人飞书 user_id / 姓名 / 待裁决标记 /
--      决策时间 / 决策结果 / 决策备注
--   2) 新增复合索引 idx_todo_pending_decision，供 post-todo-action 超时扫描使用
--
-- 执行方式（幂等，可重复）：
--   ProdSchemaMigrate --apply src/main/resources/schema-upgrade/v0.27-todo-decision-maker.sql
--   或 mysql -h ... -u ... -p intelligence < src/main/resources/schema-upgrade/v0.27-todo-decision-maker.sql
--
-- 配套代码：
--   - enums/TodoStatus.java 新增 PENDING_DECISION 枚举
--   - entity/MeetingTodo.java 同步新增 6 个字段
--   - service/oabp/OabpDecisionMakerResolver.java 决策人解析
--   - service/TodoService.java 新增 applyAssigneeComplete / applyDecisionMakerDecision
--   - pipeline/executor/PostTodoActionStepExecutor.java 改造为裁决超时扫描
--
-- 文档：docs/开关手册.md（新增"待办二段式裁决"章节）· docs/USER-MANUAL.md
-- =============================================================================


-- -----------------------------------------------------------------------------
-- §1 int_meeting_todo 新增 6 列决策人字段
--    幂等：先用 information_schema 判断列是否存在，再决定是否 ALTER
-- -----------------------------------------------------------------------------

-- 1.1 决策人飞书 user_id（缓存，避免每次回调重查 OABP jq_todos_task.decision_maker_user_id）
SET @col_decision_maker_feishu_user_id := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'int_meeting_todo'
      AND column_name = 'decision_maker_feishu_user_id'
);
SET @sql_decision_maker_feishu_user_id := IF(@col_decision_maker_feishu_user_id = 0,
    'ALTER TABLE int_meeting_todo ADD COLUMN decision_maker_feishu_user_id VARCHAR(64) NULL COMMENT ''决策人飞书user_id（缓存自OABP decision_maker_user_id解析；NULL表示无决策人，直接走原完成流程）'' AFTER operator_name',
    'SELECT ''decision_maker_feishu_user_id exists'' AS _skip');
PREPARE stmt_decision_maker_feishu_user_id FROM @sql_decision_maker_feishu_user_id;
EXECUTE stmt_decision_maker_feishu_user_id;
DEALLOCATE PREPARE stmt_decision_maker_feishu_user_id;

-- 1.2 决策人姓名（展示用，与 decision_maker_feishu_user_id 同步写入）
SET @col_decision_maker_name := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'int_meeting_todo'
      AND column_name = 'decision_maker_name'
);
SET @sql_decision_maker_name := IF(@col_decision_maker_name = 0,
    'ALTER TABLE int_meeting_todo ADD COLUMN decision_maker_name VARCHAR(100) NULL COMMENT ''决策人姓名（展示用，从int_user_mapping_feishu.user_name取）'' AFTER decision_maker_feishu_user_id',
    'SELECT ''decision_maker_name exists'' AS _skip');
PREPARE stmt_decision_maker_name FROM @sql_decision_maker_name;
EXECUTE stmt_decision_maker_name;
DEALLOCATE PREPARE stmt_decision_maker_name;

-- 1.3 pending_decision：是否处于"已提交待裁决"态
--     责任人提交完成且有决策人时置 1；决策人裁决后置 0
--     post-todo-action step 扫描此标记 + decision_made_at NULL 找超时未裁决的待办
SET @col_pending_decision := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'int_meeting_todo'
      AND column_name = 'pending_decision'
);
SET @sql_pending_decision := IF(@col_pending_decision = 0,
    'ALTER TABLE int_meeting_todo ADD COLUMN pending_decision TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否处于已提交待裁决态（0否1是；责任人完成且有决策人时置1，裁决后置0）'' AFTER decision_maker_name',
    'SELECT ''pending_decision exists'' AS _skip');
PREPARE stmt_pending_decision FROM @sql_pending_decision;
EXECUTE stmt_pending_decision;
DEALLOCATE PREPARE stmt_pending_decision;

-- 1.4 decision_made_at：决策人作出裁决的时间（用于裁决后统计与超时计算）
--     NULL 表示尚未裁决（含未进入裁决态的待办）
SET @col_decision_made_at := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'int_meeting_todo'
      AND column_name = 'decision_made_at'
);
SET @sql_decision_made_at := IF(@col_decision_made_at = 0,
    'ALTER TABLE int_meeting_todo ADD COLUMN decision_made_at DATETIME NULL COMMENT ''决策人作出裁决的时间（NULL表示尚未裁决）'' AFTER pending_decision',
    'SELECT ''decision_made_at exists'' AS _skip');
PREPARE stmt_decision_made_at FROM @sql_decision_made_at;
EXECUTE stmt_decision_made_at;
DEALLOCATE PREPARE stmt_decision_made_at;

-- 1.5 decision_result：裁决结果枚举字符串
--     APPROVED=裁决完成 / DELAYED=裁决延期 / REJECTED=驳回回进行中
SET @col_decision_result := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'int_meeting_todo'
      AND column_name = 'decision_result'
);
SET @sql_decision_result := IF(@col_decision_result = 0,
    'ALTER TABLE int_meeting_todo ADD COLUMN decision_result VARCHAR(16) NULL COMMENT ''裁决结果：APPROVED|DELAYED|REJECTED（NULL表示未裁决）'' AFTER decision_made_at',
    'SELECT ''decision_result exists'' AS _skip');
PREPARE stmt_decision_result FROM @sql_decision_result;
EXECUTE stmt_decision_result;
DEALLOCATE PREPARE stmt_decision_result;

-- 1.6 decision_note：决策人裁决备注（驳回原因、延期说明等）
SET @col_decision_note := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'int_meeting_todo'
      AND column_name = 'decision_note'
);
SET @sql_decision_note := IF(@col_decision_note = 0,
    'ALTER TABLE int_meeting_todo ADD COLUMN decision_note TEXT NULL COMMENT ''决策人裁决备注（驳回原因、延期说明等）'' AFTER decision_result',
    'SELECT ''decision_note exists'' AS _skip');
PREPARE stmt_decision_note FROM @sql_decision_note;
EXECUTE stmt_decision_note;
DEALLOCATE PREPARE stmt_decision_note;


-- -----------------------------------------------------------------------------
-- §2 新增复合索引 idx_todo_pending_decision
--    供 post-todo-action 超时扫描使用：
--    WHERE pending_decision=1 AND decision_made_at IS NULL AND created_at < ...
--    幂等：先检查索引是否存在
-- -----------------------------------------------------------------------------
SET @idx_pending_decision := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'int_meeting_todo'
      AND index_name = 'idx_todo_pending_decision'
);
SET @sql_idx_pending_decision := IF(@idx_pending_decision = 0,
    'ALTER TABLE int_meeting_todo ADD INDEX idx_todo_pending_decision (pending_decision, decision_made_at)',
    'SELECT ''idx_todo_pending_decision exists'' AS _skip');
PREPARE stmt_idx_pending_decision FROM @sql_idx_pending_decision;
EXECUTE stmt_idx_pending_decision;
DEALLOCATE PREPARE stmt_idx_pending_decision;

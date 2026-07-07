-- =============================================================================
-- 会前事项对比通报 — 手动补录 2026-07-06
-- 基准纪要：综合管理会（周例会）2026年6月29日
-- 生成时间：2026-07-06 00:45:00
-- 库：intelligence
-- 幂等：同一 output_config_name 先删后插，只保留本批次
-- 执行：mysql -h ... -u ... -p intelligence < weekly-matter-comparison-seed-2026-07-06.sql
-- 主持页：host_agenda OUTPUT generatedReportRunId = 文末 @wmc_run_id（或取 MAX id 查询结果）
-- 展示 SQL：scripts/sql/weekly-matter-comparison-agenda-display.sql
-- =============================================================================

SET @wmc_seed_generated_at := '2026-07-06 00:45:00';
SET @wmc_seed_output_config_name := 'preset1-weekly-report-out';
SET @wmc_seed_title := '会议纪要待办事项-2026-07-06';

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
    1,
    NULL,
    @wmc_seed_title,
    29,
    'READY',
    @wmc_seed_generated_at,
    NULL
);

SET @wmc_run_id := LAST_INSERT_ID();

-- § 延期事项（2）
INSERT INTO int_weekly_matter_comparison_item (
    run_id, category, matter_name, assignee, time_node, status_label, sort_order, source_config_name
) VALUES
    (@wmc_run_id, 'DELAYED', '岚图vs育喆外包服务协议/转包红线（法务提出外包协议突破主合同禁止转包红线，单院指示法务将风险告知相关方并由管理层决策）', '管小慧（法务）', '已延期', '延期', 1, NULL),
    (@wmc_run_id, 'DELAYED', '集型设计回款资料审核', '付靖怡', '6月22日纪要要求本周完成，已逾期', '延期', 2, NULL);

-- § 已完成事项（12）
INSERT INTO int_weekly_matter_comparison_item (
    run_id, category, matter_name, assignee, time_node, status_label, sort_order, source_config_name
) VALUES
    (@wmc_run_id, 'COMPLETED', 'OA平台与会议打通并完成管小慧数据录入', '贾伟光', '纪要确认已完成', '已完成', 1, NULL),
    (@wmc_run_id, 'COMPLETED', '非经营业务与上游尾款全部结清、发票提交财务', '管小慧', '纪要确认已完成', '已完成', 2, NULL),
    (@wmc_run_id, 'COMPLETED', '三层房租续租（7月1日起续租半年）', '管小慧', '已完成', '已完成', 3, NULL),
    (@wmc_run_id, 'COMPLETED', '吉青科小能力提升项目合同签订、7月正常开票结算', '付靖怡', '已完成', '已完成', 4, NULL),
    (@wmc_run_id, 'COMPLETED', '研发费核查资料补充', '付靖怡', '周六已完成', '已完成', 5, NULL),
    (@wmc_run_id, 'COMPLETED', '广州吉清获得工行250万贷款', '付靖怡', '上周已完成', '已完成', 6, NULL),
    (@wmc_run_id, 'COMPLETED', '私库合同签订、银行走免费阶段（10万本金和200元年费全免）', '付靖怡', '已完成', '已完成', 7, NULL),
    (@wmc_run_id, 'COMPLETED', '长白山实验室主体装修完毕、预计7月初入住', '郭运娇', '已完成', '已完成', 8, NULL),
    (@wmc_run_id, 'COMPLETED', '造型招聘实习生信息已发布（薪资2000-3000元）', '管小慧', '本周已完成发布', '已完成', 9, NULL),
    (@wmc_run_id, 'COMPLETED', '高端猎头已交付两个化工和职能岗位简历、等待反馈', '管小慧', '已完成交付', '已完成', 10, NULL),
    (@wmc_run_id, 'COMPLETED', '吉青集团税务从原地址迁到青岛已完成', '付靖怡', '已完成', '已完成', 11, NULL),
    (@wmc_run_id, 'COMPLETED', '培训分工与钟娜已敲定', '管小慧', '纪要确认已完成', '已完成', 12, NULL);

-- § 进行中事项（15）；time_node 为「未提及」存 NULL
INSERT INTO int_weekly_matter_comparison_item (
    run_id, category, matter_name, assignee, time_node, status_label, sort_order, source_config_name
) VALUES
    (@wmc_run_id, 'IN_PROGRESS', '月度汇报会（7月10号前以公司为单位召开，按清单汇报，评委何浩、田总、郭运娇、管小慧、付靖怡）', '付靖怡', '7月10日前', '进行中', 1, NULL),
    (@wmc_run_id, 'IN_PROGRESS', 'OA数据录入（本周录入付靖怡、李海天和郭运娇数据，下周完全切换到OA平台内部流转）', '贾伟光', '本周完成录入', '进行中', 2, NULL),
    (@wmc_run_id, 'IN_PROGRESS', '数据源同步（飞书数据源与接入OA的数据源本周完成同步）', '贾伟光/郭儒杰', '本周（约7月5日前）', '进行中', 3, NULL),
    (@wmc_run_id, 'IN_PROGRESS', '管小慧下周拜访解放和大众（沟通协会荐才及培训需求）', '管小慧', '下周（7月6日-12日）', '进行中', 4, NULL),
    (@wmc_run_id, 'IN_PROGRESS', '培训行动方案落实（与忠娜确定分工后本周落实细节和行动方案）', '管小慧', '本周', '进行中', 5, NULL),
    (@wmc_run_id, 'IN_PROGRESS', '育喆与何浩专题会（讨论未完成费用及解决方案）', '何浩、付靖怡', '本周', '进行中', 6, NULL),
    (@wmc_run_id, 'IN_PROGRESS', '科技股权培训（褚玥针对今年申报做内部培训，运营公司总经理、单院、田总参加）', '褚玥', NULL, '进行中', 7, NULL),
    (@wmc_run_id, 'IN_PROGRESS', '艾科森拨改投判断（判断明年是否符合条件，1-2周内开会，褚玥、金雷、付靖怡跟进）', '付靖怡', '1-2周内', '进行中', 8, NULL),
    (@wmc_run_id, 'IN_PROGRESS', '总经理办公会启动时尝试使用AI', '付靖怡、郭儒杰', NULL, '进行中', 9, NULL),
    (@wmc_run_id, 'IN_PROGRESS', '汽开区科技局入驻企业介绍及规划提交（提交前单院和田总审核）', '管小慧', '本周提交', '进行中', 10, NULL),
    (@wmc_run_id, 'IN_PROGRESS', '吉大共建长春检测中心合作细节商讨（本周约吴老师）', '管小慧', '本周', '进行中', 11, NULL),
    (@wmc_run_id, 'IN_PROGRESS', '具身智能训练场（与学校学生处沟通18个宿舍，本周可能有进展）', '管小慧', '本周', '进行中', 12, NULL),
    (@wmc_run_id, 'IN_PROGRESS', '山东创信工商重新开通及税务变更（卡在税务退税异常，正在联系税务局）', '付靖怡', NULL, '进行中', 13, NULL),
    (@wmc_run_id, 'IN_PROGRESS', '人才集团一汽服务能力代码统计（马总两周内完成统计并同步）', '管小慧（跟进）', '两周内', '进行中', 14, NULL),
    (@wmc_run_id, 'IN_PROGRESS', '斯利普合作（因郝总出差，等下周回来见面详谈）', '郭运娇', '待郝总返回', '进行中', 15, NULL);

-- 回填 item_count（与 INSERT 一致，便于审计）
UPDATE int_weekly_matter_comparison_run
SET item_count = (SELECT COUNT(*) FROM int_weekly_matter_comparison_item WHERE run_id = @wmc_run_id)
WHERE id = @wmc_run_id;

-- 若存在对应 job，更新 last_run_id
UPDATE int_weekly_matter_comparison_job
SET last_run_id = @wmc_run_id,
    last_run_status = 'SUCCESS',
    last_run_at = @wmc_seed_generated_at,
    last_run_error = NULL
WHERE output_config_name = @wmc_seed_output_config_name
   OR job_name = 'preset1-comprehensive-weekly';

-- =============================================================================
-- § 验收查询
-- =============================================================================

-- 本批次 run id（写入 host_agenda OUTPUT generatedReportRunId）
SELECT @wmc_run_id AS generated_report_run_id, @wmc_seed_title AS title;

SELECT category, COUNT(*) AS cnt
FROM int_weekly_matter_comparison_item
WHERE run_id = @wmc_run_id
GROUP BY category
ORDER BY FIELD(category, 'DELAYED', 'COMPLETED', 'IN_PROGRESS');

-- =============================================================================
-- § 主持页 oabpTaskSql 展示（复制下方 SELECT，勿用占位符 <<...>>）
-- 文件：scripts/sql/weekly-matter-comparison-agenda-display.sql
-- 或 OUTPUT 填 generatedReportRunId（推荐，无需 oabpTaskSql）
-- =============================================================================
/*
SELECT
  i.matter_name AS `待办事项`,
  COALESCE(NULLIF(TRIM(i.assignee), ''), '未提及') AS `责任人`,
  COALESCE(NULLIF(TRIM(i.time_node), ''), '—') AS `时间节点`,
  CASE i.status_label WHEN '延期' THEN '已延期' ELSE i.status_label END AS `状态`
FROM intelligence.int_weekly_matter_comparison_item i
INNER JOIN intelligence.int_weekly_matter_comparison_run r ON r.id = i.run_id
INNER JOIN (
  SELECT id FROM intelligence.int_weekly_matter_comparison_run
  WHERE output_config_name = 'preset1-weekly-report-out'
    AND generated_at >= DATE_SUB(CURDATE(), INTERVAL WEEKDAY(CURDATE()) DAY)
    AND generated_at < DATE_ADD(DATE_SUB(CURDATE(), INTERVAL WEEKDAY(CURDATE()) DAY), INTERVAL 7 DAY)
  ORDER BY generated_at DESC, id DESC LIMIT 1
) latest ON latest.id = r.id
ORDER BY FIELD(i.category, 'DELAYED', 'COMPLETED', 'IN_PROGRESS'), i.sort_order, i.id;
*/

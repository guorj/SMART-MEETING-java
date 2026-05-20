-- ============================================================
-- 智能会议系统 - 种子数据（DML）
-- 版本: v0.4
-- 说明: 先执行 schema.sql 建表；本脚本可重复执行且**不覆盖**已配置的飞书 URL / host_agenda
-- 执行方式: 仅 MySQL 容器首次初始化、或运维手工执行；应用启动 spring.sql.init.mode=never 不会执行
-- ============================================================

-- -----------------------------------------------------------------------------
-- 固定会务类型预设 1～5（主持议题模板 + 检点名单）
-- agenda_index 与 host_agenda.items 下标对齐：检点=0，前期项目=1 …
-- -----------------------------------------------------------------------------
INSERT INTO int_meeting_type_preset (
    code, display_name, company, department, group_name,
    schedule_note, agenda_summary, organizer_name, leader_name, participants_names, host_agenda
) VALUES
(1, '综合管理会（周会）', '吉青汽车科技集团', NULL, '会议计划表',
 '每周一 9:30', '集团综合职能事务汇报', '管小慧', '单承标',
 '单承标,田树清,郭运娇,付靖怡,管小慧,陈婉韵,李海天',
 CAST('{"items":[{"title":"会序1：会议检点","minutes":5},{"title":"会序2:前期项目汇报","minutes":25},{"title":"会序3：管小慧汇报","minutes":10},{"title":"会序4：李海天汇报","minutes":10},{"title":"会序5：陈婉韵汇报","minutes":10},{"title":"会序6：郭运娇汇报","minutes":10},{"title":"会序7：付靖怡汇报","minutes":10}]}' AS JSON)),
(2, '技术委员会（周会）', '吉青汽车科技集团', NULL, '会议计划表',
 '周一上午 10:15', '专项技术方案、项目立项可行性等技术开发相关议题', '郭儒杰', '李金雷',
 '单承标,田树清,李金雷,何浩,褚玥,董秀红,及指定相关人员',
 CAST('{"items":[{"title":"主持议题A","minutes":3},{"title":"事项进度通报","minutes":7}]}' AS JSON)),
(3, '市场经营会（月会）', '吉青汽车科技集团', NULL, '会议计划表',
 '每月 18 日前', '各中心月度营收情况、市场信息汇报', '郭运娇', '田树清',
 '单承标,田树清,郭运娇,付靖怡,管小慧,各中心负责人',
 CAST('{"items":[{"title":"主持议题A","minutes":3},{"title":"事项进度通报","minutes":7}]}' AS JSON)),
(4, '财务月会', '吉青汽车科技集团', NULL, '会议计划表',
 '每月 28 日前', '集团月度财务情况汇报', '付靖怡', '单承标',
 '单承标,郭运娇,付靖怡,管小慧',
 CAST('{"items":[{"title":"主持议题A","minutes":3},{"title":"事项进度通报","minutes":7}]}' AS JSON)),
(5, '经营委员会（半年会）', '吉青汽车科技集团', NULL, '会议计划表',
 '每年两次', '集团经营分析、规划审定、风险管控、协同决策', '管小慧', '单承标',
 '单承标,田树清,何浩,褚玥,李金雷,郭运娇,付靖怡,指定人员',
 CAST('{"items":[{"title":"主持议题A","minutes":3},{"title":"事项进度通报","minutes":7}]}' AS JSON))
ON DUPLICATE KEY UPDATE
    display_name       = VALUES(display_name),
    company            = VALUES(company),
    department         = VALUES(department),
    group_name         = VALUES(group_name),
    schedule_note      = VALUES(schedule_note),
    agenda_summary     = VALUES(agenda_summary),
    organizer_name     = VALUES(organizer_name),
    leader_name        = VALUES(leader_name),
    participants_names = VALUES(participants_names);
    -- host_agenda：已存在行不覆盖，避免重复执行种子脚本冲掉运营在库内调整的会序模板

-- -----------------------------------------------------------------------------
-- 事项进度通报 global legacy（无 preset/agenda 时旧逻辑兜底；联调可 classpath 样例）
-- -----------------------------------------------------------------------------
INSERT INTO int_matter_progress_doc_config (config_name, preset_type_code, agenda_index, resource_slot, feishu_doc_url, enabled)
VALUES ('default', NULL, NULL, 0, NULL, 1)
ON DUPLICATE KEY UPDATE enabled = VALUES(enabled);

-- -----------------------------------------------------------------------------
-- 综合管理会 preset=1：会序飞书资料（仅 feishu_doc_url）
-- 租户域名示例：ovjde0k7vc1.feishu.cn
-- -----------------------------------------------------------------------------
INSERT INTO int_matter_progress_doc_config
    (config_name, preset_type_code, agenda_index, resource_slot, feishu_doc_url, enabled)
VALUES
    ('preset1-comp-agenda-01', 1, 1, 0,
     'https://ovjde0k7vc1.feishu.cn/base/SnsXbyQ1Qa57fCsI8mrcRAIbnve?table=tbl7viO4AJ4ebD0B&view=vew3qfhSyY', 1),
    ('preset1-comp-agenda-02', 1, 2, 0,
     'https://ovjde0k7vc1.feishu.cn/docx/CzrSd90yMoKnsoxR4xGcEI5Fncf', 1),
    ('preset1-comp-agenda-03', 1, 3, 0,
     'https://ovjde0k7vc1.feishu.cn/wiki/BO4Kwdv65izpo8knLdWcr2UZns2', 1),
    ('preset1-comp-agenda-04', 1, 4, 0,
     'https://ovjde0k7vc1.feishu.cn/base/GYoHbrmQYaPtflsUubDcjGyknGd?table=tblcukp9eKr3REi7&view=vewM1Y9Vem', 1)
ON DUPLICATE KEY UPDATE
    enabled = VALUES(enabled);
    -- feishu_doc_url：已存在行不覆盖，避免重启/重复执行种子脚本冲掉生产已配置的飞书链接

-- 可选：会序 index=1 同时绑定 base + docx + wiki（resource_slot 0/1/2）
-- INSERT INTO int_matter_progress_doc_config
--     (config_name, preset_type_code, agenda_index, resource_slot, feishu_doc_url, enabled)
-- VALUES
--     ('preset1-agenda1-base', 1, 1, 0, 'https://ovjde0k7vc1.feishu.cn/base/...?table=tbl...&view=...', 1),
--     ('preset1-agenda1-docx', 1, 1, 1, 'https://ovjde0k7vc1.feishu.cn/docx/...', 1),
--     ('preset1-agenda1-wiki', 1, 1, 2, 'https://ovjde0k7vc1.feishu.cn/wiki/...', 1)
-- ON DUPLICATE KEY UPDATE feishu_doc_url = VALUES(feishu_doc_url), enabled = VALUES(enabled);

-- -----------------------------------------------------------------------------
-- preset 2～5 会序资料示例（按需取消注释并改 URL）
-- -----------------------------------------------------------------------------
-- INSERT INTO int_matter_progress_doc_config
--     (config_name, preset_type_code, agenda_index, resource_slot, feishu_doc_url, enabled)
-- VALUES
--     ('preset2-agenda-01', 2, 1, 0, 'https://YOUR.feishu.cn/docx/REPLACE_ME', 1)
-- ON DUPLICATE KEY UPDATE feishu_doc_url = VALUES(feishu_doc_url), enabled = VALUES(enabled);
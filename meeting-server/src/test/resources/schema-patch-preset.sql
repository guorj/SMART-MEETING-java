-- 兼容已存在的测试库：补充 preset 列与预设表（可重复执行）
SET @exist := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'int_meeting'
      AND COLUMN_NAME = 'preset_type_code'
);
SET @sqlstmt := IF(@exist = 0,
    'ALTER TABLE int_meeting ADD COLUMN preset_type_code TINYINT NULL COMMENT ''1-5固定会务预设 6其他'' AFTER group_name',
    'SELECT 1');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS int_meeting_type_preset (
    code                TINYINT      NOT NULL PRIMARY KEY,
    display_name        VARCHAR(120) NOT NULL,
    company             VARCHAR(200) NOT NULL,
    department          VARCHAR(200) NULL,
    group_name          VARCHAR(200) NOT NULL,
    schedule_note       VARCHAR(500) NULL,
    agenda_summary      VARCHAR(1000) NULL,
    organizer_name      VARCHAR(100) NULL,
    leader_name         VARCHAR(100) NULL,
    participants_names  TEXT       NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO int_meeting_type_preset (code, display_name, company, department, group_name, schedule_note, agenda_summary, organizer_name, leader_name, participants_names) VALUES
(1, '综合管理会（周会）', '吉青汽车科技集团', NULL, '会议计划表', '每周一 9:30', '集团综合职能事务汇报', '管小慧', '单承标', '单承标,田树清,郭运娇,付靖怡,管小慧,陈婉韵,李海天'),
(2, '技术委员会（周会）', '吉青汽车科技集团', NULL, '会议计划表', '周一上午 10:15', '专项技术方案、项目立项可行性等技术开发相关议题', '郭儒杰', '李金雷', '单承标,田树清,李金雷,何浩,褚玥,董秀红,及指定相关人员'),
(3, '市场经营会（月会）', '吉青汽车科技集团', NULL, '会议计划表', '每月 18 日前', '各中心月度营收情况、市场信息汇报', '郭运娇', '田树清', '单承标,田树清,郭运娇,付靖怡,管小慧,各中心负责人'),
(4, '财务月会', '吉青汽车科技集团', NULL, '会议计划表', '每月 28 日前', '集团月度财务情况汇报', '付靖怡', '单承标', '单承标,郭运娇,付靖怡,管小慧'),
(5, '经营委员会（半年会）', '吉青汽车科技集团', NULL, '会议计划表', '每年两次', '集团经营分析、规划审定、风险管控、协同决策', '管小慧', '单承标', '单承标,田树清,何浩,褚玥,李金雷,郭运娇,付靖怡,指定人员')
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    company = VALUES(company),
    department = VALUES(department),
    group_name = VALUES(group_name),
    schedule_note = VALUES(schedule_note),
    agenda_summary = VALUES(agenda_summary),
    organizer_name = VALUES(organizer_name),
    leader_name = VALUES(leader_name),
    participants_names = VALUES(participants_names);

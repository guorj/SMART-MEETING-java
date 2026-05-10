-- ============================================================
-- 智能会议纪要系统 - 数据库 DDL
-- 版本: v0.1
-- 日期: 2026-05-08
-- 说明: 本脚本供 docker-compose MySQL 初始化使用
-- ============================================================

-- CREATE DATABASE IF NOT EXISTS smart_meeting
--     DEFAULT CHARACTER SET utf8mb4
--     DEFAULT COLLATE utf8mb4_unicode_ci;
-- USE smart_meeting;

CREATE TABLE IF NOT EXISTS int_meeting (
    id              VARCHAR(36)  NOT NULL PRIMARY KEY COMMENT '会议UUID',
    title           VARCHAR(200) NOT NULL COMMENT '会议主题',
    agenda          JSON         NULL     COMMENT '议题列表 JSON',
    company         VARCHAR(200) NOT NULL COMMENT '所属集团',
    department      VARCHAR(200) NULL     COMMENT '集团部门',
    group_name      VARCHAR(200) NOT NULL COMMENT '会议组',
    preset_type_code TINYINT      NULL     COMMENT '1-5 固定会务预设 6 其他 NULL 无',
    status          VARCHAR(30)  NOT NULL DEFAULT 'ISSUE_COLLECTING' COMMENT '会议状态',
    creator_id      VARCHAR(64)  NOT NULL COMMENT '发起人飞书user_id',
    chat_id         VARCHAR(100) NULL     COMMENT '飞书群聊ID(用于消息推送)',
    room_id         VARCHAR(64)  NULL     COMMENT '会议室ID/视频会议ID',
    previous_meeting_id VARCHAR(36) NULL  COMMENT '上次会议ID（闭环关联）',
    scheduled_time  DATETIME     NULL     COMMENT '预定时间',
    actual_start_time DATETIME   NULL     COMMENT '实际开始时间',
    actual_end_time DATETIME     NULL     COMMENT '实际结束时间',
    duration_seconds INT         NULL     COMMENT '录音总时长(秒)',
    audio_path      VARCHAR(500) NULL     COMMENT '音频本地路径 /data/audio/{date}/{id}.pcm',
    doc_url         VARCHAR(500) NULL     COMMENT '飞书纪要文档URL',
    doc_token       VARCHAR(100) NULL     COMMENT '飞书文档token',
    recording_url   VARCHAR(500) NULL     COMMENT '录音页面URL',
    recording_token VARCHAR(500) NULL     COMMENT '录音页面JWT token',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_meeting_status (status),
    INDEX idx_meeting_creator (creator_id),
    INDEX idx_meeting_previous (previous_meeting_id),
    INDEX idx_meeting_company_group (company, group_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会议主表';

CREATE TABLE IF NOT EXISTS int_meeting_type_preset (
    code                TINYINT      NOT NULL PRIMARY KEY COMMENT '1-5 固定类型',
    display_name        VARCHAR(120) NOT NULL COMMENT '列表展示名',
    company             VARCHAR(200) NOT NULL COMMENT '所属集团',
    department          VARCHAR(200) NULL     COMMENT '部门',
    group_name          VARCHAR(200) NOT NULL COMMENT '会议组',
    schedule_note       VARCHAR(500) NULL     COMMENT '召开时间说明',
    agenda_summary      VARCHAR(1000) NULL    COMMENT '会议内容',
    organizer_name      VARCHAR(100) NULL     COMMENT '组织人',
    leader_name         VARCHAR(100) NULL     COMMENT '会议主导',
    participants_names  TEXT         NULL     COMMENT '与会人姓名，逗号或顿号分隔'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='固定会议类型预设（吉青汽车科技集团会议计划表）';

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

CREATE TABLE IF NOT EXISTS int_meeting_participant (
    id              VARCHAR(36)  NOT NULL PRIMARY KEY COMMENT '记录UUID',
    meeting_id      VARCHAR(36)  NOT NULL COMMENT '会议ID',
    user_id         VARCHAR(64)  NOT NULL COMMENT '飞书user_id',
    name            VARCHAR(100) NOT NULL COMMENT '姓名',
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '确认状态',
    feature_id      VARCHAR(100) NULL     COMMENT '讯飞ISV声纹特征ID',
    voiceprint_ready TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '声纹就绪',
    todo_count      INT          NOT NULL DEFAULT 0 COMMENT '待办总数',
    completed_count INT          NOT NULL DEFAULT 0 COMMENT '已完成数',

    FOREIGN KEY (meeting_id) REFERENCES int_meeting(id) ON DELETE CASCADE,
    UNIQUE KEY uk_meeting_user (meeting_id, user_id),
    INDEX idx_participant_meeting (meeting_id),
    INDEX idx_participant_userid (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='参会人表';

CREATE TABLE IF NOT EXISTS int_transcript_segment (
    id              VARCHAR(36)  NOT NULL PRIMARY KEY COMMENT '分段UUID',
    meeting_id      VARCHAR(36)  NOT NULL COMMENT '会议ID',
    speaker_id      VARCHAR(100) NULL     COMMENT '说话人标识(speaker_N/姓名)',
    speaker_name    VARCHAR(100) NULL     COMMENT '说话人姓名',
    start_time_ms   INT          NOT NULL COMMENT '开始时间偏移(ms)',
    end_time_ms     INT          NOT NULL COMMENT '结束时间偏移(ms)',
    text            TEXT         NOT NULL COMMENT '转写/校正后文字',
    is_final        TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否最终结果',
    confidence      DOUBLE       NULL     COMMENT 'ASR置信度(0-1)',
    corrected       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否已校正',

    FOREIGN KEY (meeting_id) REFERENCES int_meeting(id) ON DELETE CASCADE,
    INDEX idx_segment_meeting_time (meeting_id, start_time_ms)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='转录分段表';

CREATE TABLE IF NOT EXISTS int_meeting_todo (
    id              VARCHAR(36)  NOT NULL PRIMARY KEY COMMENT '待办UUID',
    meeting_id      VARCHAR(36)  NOT NULL COMMENT '来源会议ID',
    content         TEXT         NOT NULL COMMENT '待办内容',
    assignee_id     VARCHAR(64)  NOT NULL COMMENT '责任人飞书user_id',
    assignee_name   VARCHAR(100) NULL     COMMENT '责任人姓名',
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '待办状态',
    priority        VARCHAR(10)  NOT NULL DEFAULT 'MEDIUM' COMMENT '优先级',
    deadline        DATETIME     NULL     COMMENT '截止时间',
    completed_at    DATETIME     NULL     COMMENT '完成时间',
    completion_note TEXT         NULL     COMMENT '完成说明',
    block_reason    TEXT         NULL     COMMENT '卡点/延期原因',
    last_remind_at  DATETIME     NULL     COMMENT '上次提醒时间',
    remind_count    INT          NOT NULL DEFAULT 0 COMMENT '累计提醒次数',
    next_meeting_id VARCHAR(36)  NULL     COMMENT '下次会议ID',
    reported_in_next TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '是否已通报',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (meeting_id) REFERENCES int_meeting(id) ON DELETE CASCADE,
    INDEX idx_todo_meeting (meeting_id),
    INDEX idx_todo_assignee (assignee_id),
    INDEX idx_todo_status_deadline (status, deadline)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='待办表';

CREATE TABLE IF NOT EXISTS int_voiceprint (
    id              VARCHAR(36)  NOT NULL PRIMARY KEY COMMENT '记录UUID',
    user_id         INT          NOT NULL COMMENT 'OA用户ID(关联system_users)',
    user_name       VARCHAR(100) NOT NULL COMMENT '用户姓名',
    feishu_user_id  VARCHAR(100) NULL     COMMENT '飞书user_id',
    feature_id      VARCHAR(100) NOT NULL COMMENT '讯飞ISV声纹特征ID',
    group_id        VARCHAR(100) NULL     COMMENT '讯飞ISV声纹组ID',
    registered_at   DATETIME     NOT NULL COMMENT '注册时间',
    expires_at      DATETIME     NOT NULL COMMENT '过期时间',

    INDEX idx_voiceprint_feishu_user (feishu_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='声纹表';

CREATE TABLE IF NOT EXISTS int_user_mapping (
    user_id         INT          NOT NULL PRIMARY KEY COMMENT 'OA用户ID',
    user_name       VARCHAR(100) NOT NULL COMMENT '用户姓名',
    feishu_user_id  VARCHAR(100) NULL     COMMENT '飞书user_id',
    feishu_union_id VARCHAR(100) NULL     COMMENT '飞书union_id',
    feishu_open_id  VARCHAR(100) NULL     COMMENT '飞书open_id',

    INDEX idx_feishu_user_id (feishu_user_id),
    INDEX idx_feishu_open_id (feishu_open_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OA用户↔飞书ID映射表';

-- H2（MODE=MySQL）集成测试专用：无 information_schema 脚本、无 Testcontainers / 无外部 MySQL 时使用。
-- 与生产 DDL 语义对齐但类型略简化（JSON 用 VARCHAR），仅用于 junit。

DROP TABLE IF EXISTS int_meeting_minute;
DROP TABLE IF EXISTS int_transcript_segment;
DROP TABLE IF EXISTS int_meeting_todo;
DROP TABLE IF EXISTS int_meeting_participant;
DROP TABLE IF EXISTS int_meeting;
DROP TABLE IF EXISTS int_matter_progress_doc_config;
DROP TABLE IF EXISTS int_meeting_type_preset;
DROP TABLE IF EXISTS int_voiceprint;
DROP TABLE IF EXISTS int_user_mapping;

CREATE TABLE int_meeting (
    id              VARCHAR(36)  NOT NULL PRIMARY KEY,
    title           VARCHAR(200) NOT NULL,
    agenda          VARCHAR(8000) NULL,
    host_agenda     VARCHAR(8000) NULL,
    company         VARCHAR(200) NOT NULL,
    department      VARCHAR(200) NULL,
    group_name      VARCHAR(200) NOT NULL,
    preset_type_code TINYINT      NULL,
    status          VARCHAR(30)  NOT NULL DEFAULT 'ISSUE_COLLECTING',
    creator_id      VARCHAR(64)  NOT NULL,
    chat_id         VARCHAR(100) NULL,
    room_id         VARCHAR(64)  NULL,
    previous_meeting_id VARCHAR(36) NULL,
    scheduled_time  TIMESTAMP    NULL,
    actual_start_time TIMESTAMP    NULL,
    actual_end_time TIMESTAMP    NULL,
    duration_seconds INT         NULL,
    audio_path      VARCHAR(500) NULL,
    doc_url         VARCHAR(500) NULL,
    doc_token       VARCHAR(100) NULL,
    recording_url   VARCHAR(500) NULL,
    recording_token VARCHAR(500) NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE int_meeting_type_preset (
    code                TINYINT      NOT NULL PRIMARY KEY,
    display_name        VARCHAR(120) NOT NULL,
    company             VARCHAR(200) NOT NULL,
    department          VARCHAR(200) NULL,
    group_name          VARCHAR(200) NOT NULL,
    schedule_note       VARCHAR(500) NULL,
    agenda_summary      VARCHAR(1000) NULL,
    organizer_name      VARCHAR(100) NULL,
    leader_name         VARCHAR(100) NULL,
    participants_names  VARCHAR(4000) NULL,
    host_agenda         VARCHAR(8000) NULL
);

INSERT INTO int_meeting_type_preset (code, display_name, company, department, group_name, schedule_note, agenda_summary, organizer_name, leader_name, participants_names, host_agenda) VALUES
(1, '综合管理会（周会）', '吉青汽车科技集团', NULL, '会议计划表', '每周一 9:30', '集团综合职能事务汇报', '管小慧', '单承标', '单承标,田树清,郭运娇,付靖怡,管小慧,陈婉韵,李海天', '{"items":[{"title":"会序1：会议检点","minutes":5},{"title":"会序2:前期项目汇报","minutes":25},{"title":"会序3：管小慧汇报","minutes":10},{"title":"会序4：李海天汇报","minutes":10},{"title":"会序5：陈婉韵汇报","minutes":10},{"title":"会序6：郭运娇汇报","minutes":10},{"title":"会序7：付靖怡汇报","minutes":10}]}'),
(2, '技术委员会（周会）', '吉青汽车科技集团', NULL, '会议计划表', '周一上午 10:15', '专项技术方案、项目立项可行性等技术开发相关议题', '郭儒杰', '李金雷', '单承标,田树清,李金雷,何浩,褚玥,董秀红,及指定相关人员', '{"items":[{"title":"主持议题A","minutes":3},{"title":"事项进度通报","minutes":7}]}'),
(3, '市场经营会（月会）', '吉青汽车科技集团', NULL, '会议计划表', '每月 18 日前', '各中心月度营收情况、市场信息汇报', '郭运娇', '田树清', '单承标,田树清,郭运娇,付靖怡,管小慧,各中心负责人', '{"items":[{"title":"主持议题A","minutes":3},{"title":"事项进度通报","minutes":7}]}'),
(4, '财务月会', '吉青汽车科技集团', NULL, '会议计划表', '每月 28 日前', '集团月度财务情况汇报', '付靖怡', '单承标', '单承标,郭运娇,付靖怡,管小慧', '{"items":[{"title":"主持议题A","minutes":3},{"title":"事项进度通报","minutes":7}]}'),
(5, '经营委员会（半年会）', '吉青汽车科技集团', NULL, '会议计划表', '每年两次', '集团经营分析、规划审定、风险管控、协同决策', '管小慧', '单承标', '单承标,田树清,何浩,褚玥,李金雷,郭运娇,付靖怡,指定人员', '{"items":[{"title":"主持议题A","minutes":3},{"title":"事项进度通报","minutes":7}]}');

CREATE TABLE int_meeting_participant (
    id              VARCHAR(36)  NOT NULL PRIMARY KEY,
    meeting_id      VARCHAR(36)  NOT NULL,
    user_id         VARCHAR(64)  NOT NULL,
    name            VARCHAR(100) NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    attendance_mode VARCHAR(20)  NOT NULL DEFAULT 'OFFLINE',
    checked_in_at   TIMESTAMP    NULL,
    check_in_source VARCHAR(30)  NULL,
    feature_id      VARCHAR(100) NULL,
    voiceprint_ready BOOLEAN      NOT NULL DEFAULT FALSE,
    todo_count      INT          NOT NULL DEFAULT 0,
    completed_count INT          NOT NULL DEFAULT 0,
    UNIQUE (meeting_id, user_id)
);

CREATE TABLE int_transcript_segment (
    id              VARCHAR(36)  NOT NULL PRIMARY KEY,
    meeting_id      VARCHAR(36)  NOT NULL,
    speaker_id      VARCHAR(100) NULL,
    speaker_name    VARCHAR(100) NULL,
    start_time_ms   INT          NOT NULL,
    end_time_ms     INT          NOT NULL,
    text            VARCHAR(8000) NOT NULL,
    is_final        BOOLEAN      NOT NULL DEFAULT FALSE,
    confidence      DOUBLE       NULL,
    corrected       BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE TABLE int_meeting_minute (
    meeting_id          VARCHAR(36)   NOT NULL PRIMARY KEY,
    content_markdown    CLOB          NOT NULL,
    content_length      INT           NOT NULL DEFAULT 0,
    generation_status   VARCHAR(20)   NOT NULL DEFAULT 'READY',
    generated_at        TIMESTAMP     NOT NULL,
    updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE int_meeting_todo (
    id              VARCHAR(36)  NOT NULL PRIMARY KEY,
    meeting_id      VARCHAR(36)  NOT NULL,
    content         VARCHAR(8000) NOT NULL,
    assignee_id     VARCHAR(64)  NOT NULL,
    assignee_name   VARCHAR(100) NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    priority        VARCHAR(10)  NOT NULL DEFAULT 'MEDIUM',
    deadline        TIMESTAMP    NULL,
    completed_at    TIMESTAMP    NULL,
    completion_note VARCHAR(8000) NULL,
    block_reason    VARCHAR(8000) NULL,
    last_remind_at  TIMESTAMP    NULL,
    remind_count    INT          NOT NULL DEFAULT 0,
    next_meeting_id VARCHAR(36)  NULL,
    reported_in_next BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE int_voiceprint (
    id              VARCHAR(36)  NOT NULL PRIMARY KEY,
    user_id         INT          NOT NULL,
    user_name       VARCHAR(100) NOT NULL,
    feishu_user_id  VARCHAR(100) NULL,
    feature_id      VARCHAR(100) NOT NULL,
    group_id        VARCHAR(100) NULL,
    registered_at   TIMESTAMP    NOT NULL,
    expires_at      TIMESTAMP    NOT NULL
);

CREATE TABLE int_user_mapping (
    user_id         INT          NOT NULL PRIMARY KEY,
    user_name       VARCHAR(100) NOT NULL,
    feishu_user_id  VARCHAR(100) NULL,
    feishu_union_id VARCHAR(100) NULL,
    feishu_open_id  VARCHAR(100) NULL
);

CREATE TABLE int_matter_progress_doc_config (
    id                   BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    config_name          VARCHAR(64)   NOT NULL,
    preset_type_code     INT          NULL,
    agenda_index         INT          NULL,
    resource_slot        INT          NOT NULL DEFAULT 0,
    feishu_doc_url       VARCHAR(2000) NULL,
    enabled              INT          NOT NULL DEFAULT 1,
    created_at           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (config_name),
    UNIQUE (preset_type_code, agenda_index, resource_slot)
);

INSERT INTO int_matter_progress_doc_config (config_name, preset_type_code, agenda_index, resource_slot, feishu_doc_url, enabled)
VALUES ('default', NULL, NULL, 0, NULL, 1);

-- ============================================================
-- 智能会议系统 — 表结构（DDL）
-- 版本: v0.10
-- 维护: 新环境只维护本文件 + schema-data.sql；已有库只加 schema-upgrade/ 新版本
-- 共库全量: 见 ../../sql/schema-final-ddl.sql（meeting + feishu-scheduled-bot）
-- 说明: docs 见 meeting-server/src/main/resources/sql/README.md
-- ============================================================

-- CREATE DATABASE IF NOT EXISTS smart_meeting
--     DEFAULT CHARACTER SET utf8mb4
--     DEFAULT COLLATE utf8mb4_unicode_ci;
-- USE smart_meeting;

CREATE TABLE IF NOT EXISTS int_meeting (
    id                   VARCHAR(36)  NOT NULL PRIMARY KEY COMMENT '会议UUID',
    title                VARCHAR(200) NOT NULL COMMENT '会议主题',
    agenda               JSON         NULL     COMMENT '会务议程：JSON 字符串数组',
    host_agenda          JSON         NULL     COMMENT 'AI主持：{"items":[{"title","minutes","detail?","feishuDocUrl?","feishuDocs"?}]}',
    company              VARCHAR(200) NOT NULL COMMENT '所属集团',
    department           VARCHAR(200) NULL     COMMENT '集团部门',
    group_name           VARCHAR(200) NOT NULL COMMENT '会议组',
    preset_type_code     TINYINT      NULL     COMMENT '1-5 固定会务预设 6 其他 NULL 无',
    status               VARCHAR(30)  NOT NULL DEFAULT 'ISSUE_COLLECTING' COMMENT '会议状态',
    creator_id           VARCHAR(64)  NOT NULL COMMENT '发起人飞书user_id',
    chat_id              VARCHAR(100) NULL     COMMENT '飞书群聊ID',
    room_id              VARCHAR(64)  NULL     COMMENT '会议室/视频会议ID',
    meeting_scenario     VARCHAR(20)  NOT NULL DEFAULT 'OFFLINE' COMMENT '会议场景：OFFLINE|HYBRID|ONLINE',
    source_audio_url     VARCHAR(1000) NULL    COMMENT '云端录音文件URL（混合/纯线上兜底）',
    previous_meeting_id   VARCHAR(36)  NULL     COMMENT '上次会议ID',
    scheduled_time       DATETIME     NULL     COMMENT '预定时间',
    actual_start_time    DATETIME     NULL     COMMENT '实际开始时间',
    actual_end_time      DATETIME     NULL     COMMENT '实际结束时间',
    duration_seconds     INT          NULL     COMMENT '录音总时长(秒)',
    audio_path           VARCHAR(500) NULL     COMMENT '音频本地路径',
    doc_url              VARCHAR(500) NULL     COMMENT '飞书纪要文档URL',
    doc_token            VARCHAR(100) NULL     COMMENT '飞书文档token',
    recording_url        VARCHAR(500) NULL     COMMENT '历史完整URL（已废弃写入，仅只读兼容）',
    recording_token      VARCHAR(2048) NULL    COMMENT '录音页面JWT（持久化token，URL由base-url动态拼接）',
    -- v0.25 飞书 VC 云端录制接入（妙记音视频）
    vc_meeting_url       VARCHAR(512) NULL     COMMENT '飞书VC入会链接（日历vchat.meeting_url）',
    vc_minute_token      VARCHAR(64)  NULL     COMMENT '妙记token（recording_ready回调提取，24字符）',
    vc_recording_url     VARCHAR(512) NULL     COMMENT '妙记页面URL（回调event.url）',
    created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_meeting_status (status),
    INDEX idx_meeting_creator (creator_id),
    INDEX idx_meeting_previous (previous_meeting_id),
    INDEX idx_meeting_company_group (company, group_name),
    INDEX idx_meeting_vc_minute_token (vc_minute_token)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会议主表';

CREATE TABLE IF NOT EXISTS int_meeting_type_preset (
    code                 TINYINT      NOT NULL PRIMARY KEY COMMENT '1-5 固定类型',
    display_name         VARCHAR(120) NOT NULL COMMENT '列表展示名',
    company              VARCHAR(200) NOT NULL COMMENT '所属集团',
    department           VARCHAR(200) NULL     COMMENT '部门',
    group_name           VARCHAR(200) NOT NULL COMMENT '会议组',
    schedule_note        VARCHAR(500) NULL     COMMENT '召开时间说明（人读展示）',
    schedule_config      JSON         NULL     COMMENT '机器可读排期 weekly/fixed/at_start；驱动快速开始 scheduled_time',
    agenda_summary       VARCHAR(1000) NULL    COMMENT '会议内容',
    organizer_name       VARCHAR(100) NULL     COMMENT '组织人',
    leader_name          VARCHAR(100) NULL     COMMENT '会议主导',
    participants_names   TEXT         NULL     COMMENT '与会人姓名，逗号或顿号分隔',
    host_agenda          JSON         NULL     COMMENT 'AI主持议题模板 v2 {"version":2,"items":[{"title","minutes","docs":[{"configName","role","slot","url",...}]}]}',
    minute_skill_name    VARCHAR(64)  NULL     COMMENT '纪要生成 OpenClaw Skill 名；NULL 表示走 LLM 降级'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='固定会议类型预设';

CREATE TABLE IF NOT EXISTS int_meeting_participant (
    id               VARCHAR(36)  NOT NULL PRIMARY KEY COMMENT '记录UUID',
    meeting_id       VARCHAR(36)  NOT NULL COMMENT '会议ID',
    preset_type_code TINYINT      NULL     COMMENT '1-5 模板会 6 自定义，冗余自主表',
    user_id          VARCHAR(64)  NOT NULL COMMENT '飞书user_id',
    name             VARCHAR(100) NOT NULL COMMENT '姓名',
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '确认状态',
    attendance_mode  VARCHAR(20)  NOT NULL DEFAULT 'OFFLINE' COMMENT 'OFFLINE=线下答到点名 ONLINE=线上链接盘点',
    checked_in_at    DATETIME     NULL     COMMENT '到场登记时间',
    check_in_source  VARCHAR(30)  NULL     COMMENT 'AUTO_ONLINE|ROLL_CALL|MANUAL|TIMEOUT',
    feature_id       VARCHAR(100) NULL     COMMENT '讯飞声纹特征ID',
    voiceprint_ready TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '声纹就绪',
    todo_count       INT          NOT NULL DEFAULT 0 COMMENT '待办总数',
    completed_count  INT          NOT NULL DEFAULT 0 COMMENT '已完成数',

    FOREIGN KEY (meeting_id) REFERENCES int_meeting(id) ON DELETE CASCADE,
    UNIQUE KEY uk_meeting_user (meeting_id, user_id),
    INDEX idx_participant_meeting (meeting_id),
    INDEX idx_participant_preset (preset_type_code),
    INDEX idx_participant_userid (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='参会人表';

CREATE TABLE IF NOT EXISTS int_meeting_audio_asset (
    id               VARCHAR(36)   NOT NULL PRIMARY KEY COMMENT '音频资产UUID',
    meeting_id       VARCHAR(36)   NOT NULL COMMENT '会议ID',
    asset_role       VARCHAR(20)   NOT NULL COMMENT 'ORIGINAL=原始录音 NORMALIZED=标准化后供ASR/ISV',
    source_type      VARCHAR(30)   NOT NULL DEFAULT 'UNKNOWN' COMMENT 'MICROPHONE|STEREO_MIX|UPLOAD|CLOUD|UNKNOWN',
    path             VARCHAR(500)  NOT NULL COMMENT '服务器本地路径',
    encoding         VARCHAR(32)   NOT NULL DEFAULT 'pcm_s16le' COMMENT 'pcm_s16le|wav|mp3|m4a 等',
    sample_rate      INT           NOT NULL DEFAULT 16000 COMMENT '采样率Hz',
    channels         TINYINT       NOT NULL DEFAULT 1 COMMENT '声道数',
    bit_depth        TINYINT       NOT NULL DEFAULT 16 COMMENT '位深',
    file_size        BIGINT        NOT NULL DEFAULT 0 COMMENT '文件大小字节',
    duration_ms      INT           NULL     COMMENT '时长毫秒',
    rms              DOUBLE        NULL     COMMENT '均方根振幅（16bit mono 标准化后）',
    absmax           INT           NULL     COMMENT '最大绝对振幅',
    nonzero_ratio    DOUBLE        NULL     COMMENT '非零采样占比0-1',
    quality_status   VARCHAR(30)   NOT NULL DEFAULT 'OK' COMMENT 'OK|LOW_VOLUME|SILENT|FORMAT_UNKNOWN|CONVERT_FAILED|TOO_SHORT',
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (meeting_id) REFERENCES int_meeting(id) ON DELETE CASCADE,
    INDEX idx_audio_asset_meeting_role (meeting_id, asset_role),
    INDEX idx_audio_asset_meeting_created (meeting_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会议音频资产（原始/标准化）';

CREATE TABLE IF NOT EXISTS int_transcript_segment (
    id            VARCHAR(36)  NOT NULL PRIMARY KEY COMMENT '分段UUID',
    meeting_id    VARCHAR(36)  NOT NULL COMMENT '会议ID',
    preset_type_code TINYINT   NULL     COMMENT '1-5 模板会 6 自定义，冗余自主表',
    speaker_id    VARCHAR(100) NULL     COMMENT '说话人标识',
    speaker_name  VARCHAR(100) NULL     COMMENT '说话人姓名',
    start_time_ms INT          NOT NULL COMMENT '开始时间偏移(ms)',
    end_time_ms   INT          NOT NULL COMMENT '结束时间偏移(ms)',
    text          TEXT         NOT NULL COMMENT '转写/校正后文字',
    is_final      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否最终结果',
    confidence    DOUBLE       NULL     COMMENT 'ASR置信度',
    corrected     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否已校正',

    FOREIGN KEY (meeting_id) REFERENCES int_meeting(id) ON DELETE CASCADE,
    INDEX idx_segment_meeting_time (meeting_id, start_time_ms),
    INDEX idx_segment_preset (preset_type_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='转录分段表';

CREATE TABLE IF NOT EXISTS int_meeting_todo (
    id               VARCHAR(36)  NOT NULL PRIMARY KEY COMMENT '待办UUID',
    meeting_id       VARCHAR(36)  NOT NULL COMMENT '来源会议ID',
    preset_type_code TINYINT      NULL     COMMENT '1-5 模板会 6 自定义，冗余自主表',
    content          TEXT         NOT NULL COMMENT '待办内容',
    assignee_id      VARCHAR(64)  NOT NULL COMMENT '责任人飞书user_id',
    assignee_name    VARCHAR(100) NULL     COMMENT '责任人姓名',
    operator_id      VARCHAR(64)  NULL     COMMENT '经办人飞书user_id',
    operator_name    VARCHAR(100) NULL     COMMENT '经办人姓名',
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '待办状态',
    priority         VARCHAR(10)  NOT NULL DEFAULT 'MEDIUM' COMMENT '优先级',
    deadline         DATETIME     NULL     COMMENT '截止时间',
    completed_at     DATETIME     NULL     COMMENT '完成时间',
    completion_note  TEXT         NULL     COMMENT '完成说明',
    block_reason     TEXT         NULL     COMMENT '卡点/延期原因',
    last_remind_at   DATETIME     NULL     COMMENT '上次提醒时间',
    remind_count     INT          NOT NULL DEFAULT 0 COMMENT '累计提醒次数',
    next_meeting_id  VARCHAR(36)  NULL     COMMENT '下次会议ID',
    reported_in_next TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否已通报',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (meeting_id) REFERENCES int_meeting(id) ON DELETE CASCADE,
    INDEX idx_todo_meeting (meeting_id),
    INDEX idx_todo_preset (preset_type_code),
    INDEX idx_todo_assignee (assignee_id),
    INDEX idx_todo_operator (operator_id),
    INDEX idx_todo_status_deadline (status, deadline)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='待办表';

CREATE TABLE IF NOT EXISTS int_meeting_todo_progress (
    id               VARCHAR(36)  NOT NULL PRIMARY KEY COMMENT '进度记录UUID',
    todo_id          VARCHAR(36)  NOT NULL COMMENT '待办ID',
    author_id        VARCHAR(64)  NOT NULL COMMENT '提交人飞书user_id',
    author_name      VARCHAR(100) NULL     COMMENT '提交人姓名',
    author_role      VARCHAR(20)  NOT NULL COMMENT 'ASSIGNEE|OPERATOR',
    progress_text    TEXT         NOT NULL COMMENT '进度说明',
    progress_percent TINYINT      NULL     COMMENT '进度百分比0-100',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (todo_id) REFERENCES int_meeting_todo(id) ON DELETE CASCADE,
    INDEX idx_todo_progress_todo (todo_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='待办进度时间线';

CREATE TABLE IF NOT EXISTS int_meeting_todo_attachment (
    id            VARCHAR(36)  NOT NULL PRIMARY KEY COMMENT '附件UUID',
    todo_id       VARCHAR(36)  NOT NULL COMMENT '待办ID',
    progress_id   VARCHAR(36)  NULL     COMMENT '关联进度记录ID',
    uploader_id   VARCHAR(64)  NOT NULL COMMENT '上传人飞书user_id',
    uploader_name VARCHAR(100) NULL     COMMENT '上传人姓名',
    file_name     VARCHAR(255) NOT NULL COMMENT '原始文件名',
    storage_path  VARCHAR(500) NOT NULL COMMENT '服务器存储相对路径',
    file_size     BIGINT       NOT NULL DEFAULT 0 COMMENT '文件大小字节',
    mime_type     VARCHAR(128) NULL     COMMENT 'MIME类型',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (todo_id) REFERENCES int_meeting_todo(id) ON DELETE CASCADE,
    INDEX idx_todo_attachment_todo (todo_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='待办附件';

CREATE TABLE IF NOT EXISTS int_meeting_todo_audit (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    todo_id        VARCHAR(36)     NOT NULL COMMENT '待办ID',
    action         VARCHAR(32)     NOT NULL COMMENT 'ADMIN_FORCE_STATUS|ADMIN_ASSIGN|ADMIN_DELETE',
    old_status     VARCHAR(20)     NULL     COMMENT '变更前状态',
    new_status     VARCHAR(20)     NULL     COMMENT '变更后状态',
    operator_id    VARCHAR(64)     NULL     COMMENT 'Admin操作者飞书user_id',
    operator_name  VARCHAR(100)    NULL     COMMENT 'Admin操作者姓名',
    reason         VARCHAR(500)    NOT NULL COMMENT '强制操作原因',
    payload_json   JSON            NULL     COMMENT '其他变更快照',
    created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_todo_audit_todo (todo_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='待办Admin强制操作审计';

CREATE TABLE IF NOT EXISTS int_meeting_minute (
    meeting_id          VARCHAR(36)   NOT NULL PRIMARY KEY COMMENT '会议UUID',
    preset_type_code    TINYINT       NULL     COMMENT '1-5 模板会 6 自定义，冗余自主表',
    content_markdown    LONGTEXT      NOT NULL COMMENT '纪要正文 Markdown',
    content_url         VARCHAR(300)  NULL     COMMENT '正文对应文档链接（如飞书纪要 URL）',
    content_length      INT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '正文字符数',
    generation_status   VARCHAR(20)   NOT NULL DEFAULT 'READY' COMMENT 'READY|FAILED|PARTIAL',
    generated_at        DATETIME      NOT NULL COMMENT '纪要生成时间',
    updated_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (meeting_id) REFERENCES int_meeting(id) ON DELETE CASCADE,
    INDEX idx_minute_preset (preset_type_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会议纪要正文（最新一版）';

CREATE TABLE IF NOT EXISTS int_voiceprint (
    id             VARCHAR(36)  NOT NULL PRIMARY KEY COMMENT '记录UUID',
    user_id        INT          NOT NULL COMMENT 'OA用户ID',
    user_name      VARCHAR(100) NOT NULL COMMENT '用户姓名',
    feishu_user_id VARCHAR(100) NULL     COMMENT '飞书user_id',
    feature_id     VARCHAR(100) NOT NULL COMMENT '讯飞声纹特征ID',
    group_id       VARCHAR(100) NULL     COMMENT '讯飞声纹组ID',
    registered_at  DATETIME     NOT NULL COMMENT '注册时间',
    expires_at     DATETIME     NOT NULL COMMENT '过期时间',

    INDEX idx_voiceprint_feishu_user (feishu_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='声纹表';

CREATE TABLE IF NOT EXISTS int_user_mapping_feishu (
    user_id         INT          NOT NULL PRIMARY KEY COMMENT 'OA用户ID',
    user_name       VARCHAR(100) NOT NULL COMMENT '用户姓名',
    feishu_user_id  VARCHAR(100) NULL     COMMENT '飞书user_id（企业内唯一，业务主键）',

    INDEX idx_feishu_user_id (feishu_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OA用户↔飞书user_id映射表';

-- int_matter_progress_doc_config 已废弃：资料内嵌 int_meeting_type_preset.host_agenda v2（见 v0.14/v0.15 升级脚本）

-- v0.12 系统参数（meeting-admin-server 写，meeting-server reload）
CREATE TABLE IF NOT EXISTS int_meeting_system_config (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    config_key   VARCHAR(128)    NOT NULL COMMENT '参数键',
    category     VARCHAR(32)     NOT NULL DEFAULT 'host' COMMENT '分组',
    value_json   JSON            NOT NULL COMMENT '参数值',
    description  VARCHAR(500)    NULL COMMENT '说明',
    updated_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_meeting_system_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='会议系统可热改参数';

-- v0.13 系统参数变更审计
CREATE TABLE IF NOT EXISTS int_meeting_system_config_audit (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    config_key     VARCHAR(128)    NOT NULL COMMENT '参数键',
    action         VARCHAR(16)     NOT NULL COMMENT 'UPSERT|DELETE',
    old_value_json JSON            NULL COMMENT '变更前',
    new_value_json JSON            NULL COMMENT '变更后',
    operator       VARCHAR(64)     NULL COMMENT '操作者',
    created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_config_audit_key_time (config_key, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='系统参数变更审计';

CREATE TABLE IF NOT EXISTS int_event_outbox (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    aggregate_type VARCHAR(64)     NOT NULL COMMENT '聚合类型，如 MEETING',
    aggregate_id   VARCHAR(64)     NOT NULL COMMENT '聚合 ID',
    event_type     VARCHAR(64)     NOT NULL COMMENT '事件类型',
    event_key      VARCHAR(128)    NOT NULL COMMENT '幂等键',
    payload_json   JSON            NOT NULL COMMENT '事件载荷',
    status         VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING|RETRY|PUBLISHED|FAILED',
    retry_count    INT             NOT NULL DEFAULT 0 COMMENT '重试次数',
    next_retry_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下次重试时间',
    error_message  VARCHAR(500)    NULL COMMENT '错误摘要',
    published_at   DATETIME        NULL COMMENT '最终投递成功时间',
    created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_event_outbox_key (event_key),
    KEY idx_event_outbox_scan (status, next_retry_at),
    KEY idx_event_outbox_aggregate (aggregate_type, aggregate_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='事务外盒事件表';

CREATE TABLE IF NOT EXISTS int_pipeline_template (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '模板 ID',
    template_code  VARCHAR(64)     NOT NULL COMMENT '模板编码',
    template_name  VARCHAR(120)    NOT NULL COMMENT '模板名称',
    stage          VARCHAR(16)     NOT NULL COMMENT 'PRE|MID|POST',
    enabled        TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否启用',
    version_no     INT             NOT NULL DEFAULT 1 COMMENT '版本号',
    description    VARCHAR(500)    NULL COMMENT '模板说明',
    created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_pipeline_template_code (template_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Pipeline 模板定义';

CREATE TABLE IF NOT EXISTS int_pipeline_step (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '步骤 ID',
    template_id      BIGINT UNSIGNED NOT NULL COMMENT '模板 ID',
    step_code        VARCHAR(64)     NOT NULL COMMENT '步骤编码',
    step_name        VARCHAR(120)    NOT NULL COMMENT '步骤名称',
    step_type        VARCHAR(64)     NOT NULL COMMENT '执行器类型',
    stage            VARCHAR(16)     NOT NULL COMMENT 'PRE|MID|POST',
    order_no         INT             NOT NULL COMMENT '顺序',
    timeout_seconds  INT             NULL COMMENT '超时时间（秒）',
    config_json      JSON            NULL COMMENT '步骤配置',
    enabled          TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_pipeline_step_code (template_id, step_code),
    KEY idx_pipeline_step_template_order (template_id, order_no),
    CONSTRAINT fk_pipeline_step_template FOREIGN KEY (template_id)
        REFERENCES int_pipeline_template (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Pipeline 步骤定义';

CREATE TABLE IF NOT EXISTS int_pipeline_step_execution (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '执行 ID',
    meeting_id       VARCHAR(36)     NOT NULL COMMENT '会议 ID',
    template_id      BIGINT UNSIGNED NOT NULL COMMENT '模板 ID',
    step_id          BIGINT UNSIGNED NOT NULL COMMENT '步骤 ID',
    stage            VARCHAR(16)     NOT NULL COMMENT 'PRE|MID|POST',
    status           VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING|RUNNING|SUCCESS|FAILED|WAITING|WAITING_CALLBACK|TIMEOUT',
    retry_count      INT             NOT NULL DEFAULT 0 COMMENT '重试次数',
    max_retries      INT             NOT NULL DEFAULT 3 COMMENT '最大重试',
    timeout_at       DATETIME        NULL COMMENT '超时截止时间',
    started_at       DATETIME        NULL COMMENT '开始执行时间',
    ended_at         DATETIME        NULL COMMENT '结束执行时间',
    last_error       VARCHAR(500)    NULL COMMENT '最后错误',
    context_json     JSON            NULL COMMENT '执行上下文',
    created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_pipeline_exec_meeting_stage (meeting_id, stage, status),
    KEY idx_pipeline_exec_timeout (status, timeout_at),
    CONSTRAINT fk_pipeline_exec_template FOREIGN KEY (template_id)
        REFERENCES int_pipeline_template (id) ON DELETE CASCADE,
    CONSTRAINT fk_pipeline_exec_step FOREIGN KEY (step_id)
        REFERENCES int_pipeline_step (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Pipeline 步骤运行时';

CREATE TABLE IF NOT EXISTS int_processed_command (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    command_key     VARCHAR(128)    NOT NULL COMMENT '命令幂等键',
    command_type    VARCHAR(64)     NOT NULL COMMENT '命令类型',
    aggregate_type  VARCHAR(64)     NOT NULL COMMENT '聚合类型',
    aggregate_id    VARCHAR(64)     NOT NULL COMMENT '聚合 ID',
    processed_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '处理时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_processed_command_key (command_key),
    KEY idx_processed_command_aggregate (aggregate_type, aggregate_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='幂等命令处理记录';

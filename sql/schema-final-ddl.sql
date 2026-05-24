-- ============================================================
-- intelligence 库 — 业务表完整 DDL（权威版）
-- 生成: 2026-05-23
-- 维护: 改 meeting 结构 → 先改 meeting-server/.../schema.sql，再同步本节
--       改 bot 结构 → 先改 feishu-scheduled-bot/db/migration/V*.sql，再同步第二节
-- 依据:
--   meeting-server/src/main/resources/schema.sql (v0.10)
--   meeting-server/src/main/resources/schema-upgrade/v*.sql（历史增量，已折叠进 schema.sql）
--   feishu-scheduled-bot Flyway V1–V6
-- 说明: CREATE IF NOT EXISTS；新环境可整文件执行。已有库见 sql/README.md
-- ============================================================

-- CREATE DATABASE IF NOT EXISTS intelligence
--     DEFAULT CHARACTER SET utf8mb4
--     DEFAULT COLLATE utf8mb4_unicode_ci;
-- USE intelligence;

-- =============================================================================
-- 一、智能会议（smart-meeting-java）— 11 张表
-- =============================================================================

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
    previous_meeting_id  VARCHAR(36)  NULL     COMMENT '上次会议ID',
    scheduled_time       DATETIME     NULL     COMMENT '预定时间',
    actual_start_time    DATETIME     NULL     COMMENT '实际开始时间',
    actual_end_time      DATETIME     NULL     COMMENT '实际结束时间',
    duration_seconds     INT          NULL     COMMENT '录音总时长(秒)',
    audio_path           VARCHAR(500) NULL     COMMENT '音频本地路径',
    doc_url              VARCHAR(500) NULL     COMMENT '飞书纪要文档URL',
    doc_token            VARCHAR(100) NULL     COMMENT '飞书文档token',
    recording_url        VARCHAR(500) NULL     COMMENT '录音页面URL',
    recording_token      VARCHAR(500) NULL     COMMENT '录音页面JWT',
    created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_meeting_status (status),
    INDEX idx_meeting_creator (creator_id),
    INDEX idx_meeting_previous (previous_meeting_id),
    INDEX idx_meeting_company_group (company, group_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会议主表';

CREATE TABLE IF NOT EXISTS int_meeting_type_preset (
    code                 TINYINT      NOT NULL PRIMARY KEY COMMENT '1-5 固定类型',
    display_name         VARCHAR(120) NOT NULL COMMENT '列表展示名',
    company              VARCHAR(200) NOT NULL COMMENT '所属集团',
    department           VARCHAR(200) NULL     COMMENT '部门',
    group_name           VARCHAR(200) NOT NULL COMMENT '会议组',
    schedule_note        VARCHAR(500) NULL     COMMENT '召开时间说明',
    agenda_summary       VARCHAR(1000) NULL    COMMENT '会议内容',
    organizer_name       VARCHAR(100) NULL     COMMENT '组织人',
    leader_name          VARCHAR(100) NULL     COMMENT '会议主导',
    participants_names   TEXT         NULL     COMMENT '与会人姓名，逗号或顿号分隔',
    host_agenda          JSON         NULL     COMMENT 'AI主持议题模板 {"items":[{"title","minutes","detail?","feishuDocUrl?","feishuDocs"?},...]}'
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
    INDEX idx_todo_status_deadline (status, deadline)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='待办表';

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
    id              VARCHAR(36)  NOT NULL PRIMARY KEY COMMENT '记录UUID',
    user_id         INT          NOT NULL COMMENT 'OA用户ID',
    user_name       VARCHAR(100) NOT NULL COMMENT '用户姓名',
    feishu_user_id  VARCHAR(100) NULL     COMMENT '飞书user_id',
    feature_id      VARCHAR(100) NOT NULL COMMENT '讯飞声纹特征ID',
    group_id        VARCHAR(100) NULL     COMMENT '讯飞声纹组ID',
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

CREATE TABLE IF NOT EXISTS int_matter_progress_doc_config (
    id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    config_name          VARCHAR(64)   NOT NULL COMMENT '逻辑配置名，全局唯一',
    preset_type_code     INT UNSIGNED  NULL     COMMENT '会务预设 1-5；NULL 为全局 legacy',
    agenda_index         INT UNSIGNED  NULL     COMMENT 'host_agenda.items 下标（0-based）',
    resource_slot        INT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '同会序多份资料槽位 0,1,2…',
    feishu_doc_url       VARCHAR(2000) NULL     COMMENT '飞书链接：/docx/、/wiki/、/base/?table=',
    enabled              TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '0关闭 1启用',
    config_role          VARCHAR(16)   NOT NULL DEFAULT 'SOURCE' COMMENT 'SOURCE|OUTPUT|BOTH',
    bitable_display_mode VARCHAR(16)   NOT NULL DEFAULT 'GROUPED' COMMENT 'RAW=会中平铺；GROUPED=近三月+完成/延期/进行中',
    generated_report_url VARCHAR(2000) NULL     COMMENT 'bot 写回：会前对比通报只读链接',
    generated_report_at  DATETIME      NULL     COMMENT 'bot 写回时间',
    created_at           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_matter_progress_config_name (config_name),
    UNIQUE KEY uk_preset_agenda_doc (preset_type_code, agenda_index, resource_slot),
    KEY idx_matter_progress_enabled_id (enabled, id),
    KEY idx_matter_progress_preset (preset_type_code, enabled, agenda_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='会序飞书资料配置（SOURCE 合并议程；OUTPUT 写回 generated_report_url）';

CREATE TABLE IF NOT EXISTS int_weekly_matter_comparison_job (
    id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    job_name              VARCHAR(64)     NOT NULL COMMENT '任务名，全局唯一',
    enabled               TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '0关闭 1启用',
    cron_expression       VARCHAR(64)     NOT NULL DEFAULT '0 10 * * MON' COMMENT 'Quartz cron',
    schedule_timezone     VARCHAR(64)     NOT NULL DEFAULT 'Asia/Shanghai' COMMENT 'Cron 解析时区',
    source_config_names   JSON            NOT NULL COMMENT 'SOURCE/BOTH config_name 列表',
    minute_query_type     VARCHAR(32)     NOT NULL COMMENT 'PRESET_LAST_7_DAYS|MEETING_IDS',
    minute_query_params   JSON            NOT NULL COMMENT '纪要查询参数 JSON',
    output_config_name    VARCHAR(64)     NOT NULL COMMENT '写回 config_name（OUTPUT/BOTH）',
    output_doc_title_tpl  VARCHAR(200)    NOT NULL DEFAULT '事项对比通报-{date}' COMMENT '飞书 Doc 标题模板',
    feishu_folder_token   VARCHAR(128)    NULL     COMMENT '可选：Doc 创建目录 token',
    last_run_at           DATETIME        NULL     COMMENT '最近一次执行时间',
    last_run_status       VARCHAR(20)     NULL     COMMENT 'SUCCESS|FAILED',
    last_run_error        TEXT            NULL     COMMENT '失败时错误摘要',
    created_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_weekly_comparison_job_name (job_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='每周事项对比定时任务（feishu-scheduled-bot + matter-progress-core）';

-- =============================================================================
-- 二、飞书定时推送（feishu-scheduled-bot Flyway V1–V6）— 5 张表
-- =============================================================================

CREATE TABLE IF NOT EXISTS int_scheduled_push_task (
    id               VARCHAR(36)   NOT NULL PRIMARY KEY,
    task_name        VARCHAR(100)  NOT NULL COMMENT '展示用任务名称',
    cron_expr        VARCHAR(50)   NULL     COMMENT 'Quartz Cron；EXTERNAL 模式可 NULL',
    target_type      VARCHAR(10)   NOT NULL COMMENT 'USER|GROUP（legacy 单 target，V6 起优先 targets 子表）',
    target_id        VARCHAR(100)  NOT NULL COMMENT '与 target_type 对应的 receive_id',
    message_text     TEXT          NOT NULL COMMENT '纯文本消息正文',
    enabled          TINYINT(1)    NOT NULL DEFAULT 1,
    schedule_mode    VARCHAR(10)   NOT NULL DEFAULT 'INTERNAL' COMMENT 'INTERNAL|EXTERNAL',
    schedule_tz      VARCHAR(30)   NULL     COMMENT '任务级 IANA 时区',
    skip_holidays    TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '法定假日跳过',
    misfire_policy   VARCHAR(20)   NULL DEFAULT 'SMART' COMMENT 'SMART|DROP_ALL|SINGLE|ALL',
    schedule_version BIGINT        NOT NULL DEFAULT 0 COMMENT '元数据变更版本号',
    description      VARCHAR(500)  NULL,
    created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='飞书定时推送任务';

CREATE TABLE IF NOT EXISTS int_scheduled_push_task_target (
    id           VARCHAR(36)  NOT NULL PRIMARY KEY,
    task_id      VARCHAR(36)  NOT NULL,
    target_type  VARCHAR(10)  NOT NULL COMMENT 'USER|GROUP',
    target_id    VARCHAR(100) NOT NULL,
    sort_order   INT          NOT NULL DEFAULT 0 COMMENT '同任务内推送顺序',
    CONSTRAINT fk_int_sch_task_target_task
        FOREIGN KEY (task_id) REFERENCES int_scheduled_push_task(id) ON DELETE CASCADE,
    CONSTRAINT uq_int_sch_task_target UNIQUE (task_id, target_type, target_id),
    INDEX idx_int_sch_task_target_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务多接收方（V6 批量推送）';

CREATE TABLE IF NOT EXISTS int_scheduled_task_extra_date (
    id          VARCHAR(36) NOT NULL PRIMARY KEY,
    task_id     VARCHAR(36) NOT NULL,
    extra_date  DATE        NOT NULL,
    FOREIGN KEY (task_id) REFERENCES int_scheduled_push_task(id) ON DELETE CASCADE,
    INDEX idx_int_sch_extra_date_date (extra_date),
    INDEX idx_int_sch_extra_date_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='额外推送日';

CREATE TABLE IF NOT EXISTS int_scheduled_task_exclude_date (
    id            VARCHAR(36) NOT NULL PRIMARY KEY,
    task_id       VARCHAR(36) NOT NULL,
    exclude_date  DATE        NOT NULL,
    FOREIGN KEY (task_id) REFERENCES int_scheduled_push_task(id) ON DELETE CASCADE,
    INDEX idx_int_sch_exclude_date_date (exclude_date),
    INDEX idx_int_sch_exclude_date_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='排除推送日';

CREATE TABLE IF NOT EXISTS int_scheduled_push_log (
    id                  VARCHAR(36)   NOT NULL PRIMARY KEY,
    task_id             VARCHAR(36)   NOT NULL,
    task_name           VARCHAR(100)  NOT NULL,
    meeting_id          VARCHAR(36)   NULL     COMMENT '关联会议 UUID（EVENT 推送，V4）',
    source_event_type   VARCHAR(40)   NULL     COMMENT 'MINUTE_READY|TODO_SYNC|TODO_PROGRESS 等',
    idempotency_key     VARCHAR(128)  NULL     COMMENT '业务幂等键（per-target）',
    batch_id            VARCHAR(36)   NULL     COMMENT '批量推送批次 ID（V6 /api/push/batch）',
    target_type         VARCHAR(10)   NOT NULL,
    target_id           VARCHAR(100)  NOT NULL,
    message_text        TEXT          NOT NULL,
    status              VARCHAR(10)   NOT NULL COMMENT 'SUCCESS|FAILED|SKIPPED',
    skip_reason         VARCHAR(20)   NULL,
    error_message       VARCHAR(1000) NULL,
    trigger_type        VARCHAR(15)   NOT NULL DEFAULT 'SCHEDULED' COMMENT 'SCHEDULED|MISFIRE|MANUAL|EXTRA_DATE|EVENT',
    response_code       VARCHAR(20)   NULL,
    feishu_cost_ms      BIGINT        NULL,
    total_cost_ms       BIGINT        NULL,
    send_time           TIMESTAMP     NOT NULL,
    feishu_message_id   VARCHAR(64)   NULL     COMMENT '飞书 message_id（V2 已读轮询）',
    read_poll_status    VARCHAR(20)   NULL,
    read_count          INT           NOT NULL DEFAULT 0,
    last_read_poll_at   TIMESTAMP     NULL,
    read_poll_error     VARCHAR(500)  NULL,

    INDEX idx_int_sch_log_task_id (task_id),
    INDEX idx_int_sch_log_send_time (send_time),
    INDEX idx_int_sch_log_status (status),
    INDEX idx_int_sch_log_meeting_id (meeting_id),
    INDEX idx_int_sch_log_idempotency (idempotency_key),
    INDEX idx_int_sch_log_batch_id (batch_id),
    INDEX idx_int_sch_log_msg_id (feishu_message_id),
    INDEX idx_int_sch_log_read_poll (read_poll_status, send_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='飞书推送流水';

CREATE TABLE IF NOT EXISTS int_scheduled_push_log_read_user (
    id           VARCHAR(36)  NOT NULL PRIMARY KEY,
    push_log_id  VARCHAR(36)  NOT NULL,
    user_id_type VARCHAR(20)  NOT NULL DEFAULT 'open_id',
    user_id      VARCHAR(100) NOT NULL,
    read_at      TIMESTAMP    NOT NULL,
    tenant_key   VARCHAR(64)  NULL,
    user_name    VARCHAR(100) NULL     COMMENT 'V3 已读用户姓名',
    FOREIGN KEY (push_log_id) REFERENCES int_scheduled_push_log(id) ON DELETE CASCADE,
    UNIQUE KEY uk_push_log_user (push_log_id, user_id),
    INDEX idx_int_sch_log_read_user_log (push_log_id),
    INDEX idx_int_sch_log_read_user_at (read_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='推送消息已读用户明细';

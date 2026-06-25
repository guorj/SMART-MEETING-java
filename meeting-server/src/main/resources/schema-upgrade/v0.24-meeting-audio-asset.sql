-- =============================================================================
-- v0.24 会议音频资产表（多音源原始/标准化 PCM 元信息与质量指标）
-- =============================================================================
-- 用途：兼容麦克风、系统混音、云端上传等多种输入；ASR/ISV 固定消费 NORMALIZED 资产。
-- 幂等：CREATE TABLE IF NOT EXISTS；可重复执行。
-- =============================================================================

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

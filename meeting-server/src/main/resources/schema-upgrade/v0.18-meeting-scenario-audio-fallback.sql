-- ============================================================
-- v0.18 会议三场景与云端音频兜底字段升级
-- 目标：
--   1) 支撑 OFFLINE/HYBRID/ONLINE 三类会议场景
--   2) 支撑混合/纯线上在无本地 PCM 时，使用云端录音 URL 兜底
-- 幂等策略：
--   - 仅新增列，不删除/不重命名旧列
--   - IF NOT EXISTS 保证重复执行安全
-- ============================================================

ALTER TABLE int_meeting
    ADD COLUMN IF NOT EXISTS meeting_scenario VARCHAR(20) NOT NULL DEFAULT 'OFFLINE'
        COMMENT '会议场景：OFFLINE|HYBRID|ONLINE',
    ADD COLUMN IF NOT EXISTS source_audio_url VARCHAR(1000) NULL
        COMMENT '云端录音文件URL（混合/纯线上兜底）';

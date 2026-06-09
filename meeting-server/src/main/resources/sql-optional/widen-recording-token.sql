-- 一次性运维：放宽 recording_token 长度（JWT 可能超过 500 字符）
-- 应用层不再写入 recording_url 完整 URL，仅持久化 recording_token；本脚本仍保留 recording_url 列以兼容历史数据。
-- 幂等：重复执行 MODIFY 同类型无影响。

ALTER TABLE int_meeting
    MODIFY COLUMN recording_token VARCHAR(2048) NULL COMMENT '录音页面 JWT（持久化 token，URL 由 meeting.base-url 动态拼接）';

ALTER TABLE int_meeting
    MODIFY COLUMN recording_url VARCHAR(500) NULL COMMENT '历史完整 URL（已废弃写入，仅只读兼容）';

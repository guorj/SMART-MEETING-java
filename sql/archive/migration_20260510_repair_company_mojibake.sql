-- 一次性修复：历史错误字符集写入导致的预设/会议「公司」乱码（执行前请备份）
-- 依赖应用与 JDBC 已统一为 UTF-8 / utf8mb4 后再跑，避免再次写坏。

UPDATE int_meeting_type_preset
SET company = '吉青汽车科技集团'
WHERE code BETWEEN 1 AND 5;

UPDATE int_meeting m
    INNER JOIN int_meeting_type_preset p ON p.code = m.preset_type_code
SET m.company = p.company
WHERE m.preset_type_code BETWEEN 1 AND 5;

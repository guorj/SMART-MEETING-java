-- =============================================================================
-- int_meeting_type_preset.host_agenda 入库模板（与 code 1～5 一一对应）
--
-- 业务规则：新建会议 preset_type_code 为 1～5 时，主持议程仅按该 code 读取本表行，
--           不再使用 host_agenda_template_meeting_id 等指向 int_meeting 的关联。
-- JSON 格式须与后端解析一致：{"items":[{"title":"...","minutes":整数}, ...]}
-- 代码内保底常量：com.smartmeeting.constants.HostAgendaConstants.DEFAULT_HOST_AGENDA_JSON
-- =============================================================================

-- 更新某一固定会务类型的主持模板（按需改 code 与 JSON）
UPDATE int_meeting_type_preset
SET host_agenda = CAST(
    '{"items":[{"title":"开场","minutes":5},{"title":"讨论","minutes":20},{"title":"总结","minutes":10}]}'
    AS JSON
)
WHERE code = 1;

-- 批量写入默认议题（与常量一致，便于对齐）
UPDATE int_meeting_type_preset
SET host_agenda = CAST(
    '{"items":[{"title":"主持议题A","minutes":3},{"title":"主持议题B","minutes":7}]}'
    AS JSON
)
WHERE code IN (1, 2, 3, 4, 5);

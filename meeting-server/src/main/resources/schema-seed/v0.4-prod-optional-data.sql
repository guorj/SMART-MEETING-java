-- ============================================================
-- 可选数据修正（会改业务数据，勿绑定应用启动；须显式手工执行）
-- 用法: ProdSchemaMigrate --apply src/main/resources/schema-seed/v0.4-prod-optional-data.sql
-- ============================================================

-- 强制将 preset=1 主持模板恢复为仓库默认 7 会序（会覆盖库内已调整的 host_agenda）
UPDATE int_meeting_type_preset
SET host_agenda = CAST('{"items":[{"title":"会序1：会议检点","minutes":5},{"title":"会序2:前期项目汇报","minutes":25},{"title":"会序3：管小慧汇报","minutes":10},{"title":"会序4：李海天汇报","minutes":10},{"title":"会序5：陈婉韵汇报","minutes":10},{"title":"会序6：郭运娇汇报","minutes":10},{"title":"会序7：付靖怡汇报","minutes":10}]}' AS JSON)
WHERE code = 1;

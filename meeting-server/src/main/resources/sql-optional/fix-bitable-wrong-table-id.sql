-- 修正多维表格 URL 中 table_id / app_token 大小写笔误（1254004 WrongTableId）
-- 执行前请在飞书浏览器地址栏核对 ?table=tbl... 与 /base/{app_token} 完全一致（区分大小写）

-- 配置表默认链接（preset=1 会序 index=4，resource_slot=0）
UPDATE int_matter_progress_doc_config
SET feishu_doc_url = 'https://ovjde0k7vc1.feishu.cn/base/GYoHbrmQYaPtflsUubDcJGyknGd?table=tblcukp9eKr3REI7&view=vewM1Y9Vem'
WHERE preset_type_code = 1 AND agenda_index = 4 AND resource_slot = 0;

-- 预设模板 host_agenda 会序 2（items[1]，按现网下标调整）
-- UPDATE int_meeting_type_preset
-- SET host_agenda = JSON_SET(
--   host_agenda,
--   '$.items[1].feishuDocUrl', 'https://ovjde0k7vc1.feishu.cn/base/GYoHbrmQYaPtflsUubDcJGyknGd?table=tblcukp9eKr3REI7&view=vewM1Y9Vem'
-- )
-- WHERE code = 1;

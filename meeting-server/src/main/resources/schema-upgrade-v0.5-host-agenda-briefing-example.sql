-- 可选运维脚本：为 preset=1 的指定会序启用 OpenClaw 会序通报（勿随应用自动执行）
-- 下标 items[N] 须与现网 host_agenda 一致；示例为 index=1（第二条会序）
UPDATE int_meeting_type_preset
SET host_agenda = JSON_SET(
  host_agenda,
  '$.items[1].openclawBriefing', CAST(true AS JSON),
  '$.items[1].feishuDocUrl', 'https://ovjde0k7vc1.feishu.cn/base/GYoHbrmQYaPtflsUubDcJGyknGd?table=tblcukp9eKr3REI7&view=vewM1Y9Vem'
)
WHERE code = 1;

-- ⚠️ legacy v0.5：会中 OpenClaw 通报示例（AgendaBriefingService 已删，v0.10 已 DROP openclaw_briefing 列）
-- 勿再执行；保留仅供历史参考
-- 下标 items[N] 须与现网 host_agenda 一致；示例为 index=1（第二条会序）
UPDATE int_meeting_type_preset
SET host_agenda = JSON_SET(
  host_agenda,
  '$.items[1].openclawBriefing', CAST(true AS JSON),
  '$.items[1].feishuDocUrl', 'https://ovjde0k7vc1.feishu.cn/base/GYoHbrmQYaPtflsUubDcJGyknGd?table=tblcukp9eKr3REI7&view=vewM1Y9Vem'
)
WHERE code = 1;

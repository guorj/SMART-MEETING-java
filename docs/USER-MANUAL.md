# 智能会议系统 - 用户手册（精简版）

文档版本：2026-06-04（与当前代码实现对齐）

---

## 1. 当前系统现状

- 主服务：`meeting-server`（建会、录音、主持、纪要、待办）。
- 辅助服务：`feishu-scheduled-bot`（会前对比等定时能力）。
- 会议类型：统一按 `presetTypeCode > 0` 作为模板会处理。
- 会序资料：模板权威在 `int_meeting_type_preset.host_agenda`，建会时写入 `int_meeting.host_agenda` 快照。
- 主持页读取：会中优先读取 `int_meeting.host_agenda`；支持展示 `docs[].role=SOURCE/BOTH/OUTPUT`。

---

## 2. 关键业务流程

1. 飞书发送“会议管理”进入工作台。
2. 选择模板创建会议。
3. 建会时按模板最新 `host_agenda` 生成会议快照。
4. 进入主持页后开启会话与录音。
5. 结束会议后根据开关触发纪要与待办链路。

### 2.1 录音与转写（默认）

- **默认**：`meeting.asr.realtime-enabled=false`，会中仅浏览器 WebSocket 写入单场 `{meetingId}.pcm`，无实时字幕。
- **会后**：无会中定稿转写时，系统对 PCM 做离线转写（说话人分离）+ 声纹 1:N，结果写入 `int_transcript_segment` 并用于纪要。
- **可选**：将 `realtime-enabled` 设为 `true` 可恢复会中实时转写与字幕；此时纪要以会中分段为准，不覆盖为离线结果。
- 会前建议完成参会人声纹注册（见飞书「声纹注册」入口），详见 `开关手册.md` §6.2。

---

## 3. 会序资料机制（重点）

### 3.1 为什么会有快照

- 允许同模板下的不同会议做独立回填与微调。
- 会议进行中不受模板实时变更干扰。

### 3.2 快照何时更新

- 建会时：`createMeeting` 写入会议 `host_agenda`。
- 会前回填提交时：按 meeting 维度更新 `host_agenda`。
- 内部刷新接口：`/api/v1/internal/meetings/refresh-host-agenda` 可重建快照。

### 3.3 常见误区

- 修改模板后，已建会议不会自动跟随模板变化。
- 新建会议若资料为空，优先检查新会议的 `int_meeting.host_agenda` 是否已带 `docs[]`。

---

## 4. 已知行为与限制

- AI 主持会中会对群消息做静音抑制；部分群卡片会被拦截（日志会打印 `Feishu send suppressed`）。
- 待办提醒若未关闭且责任人是无效 `user_id`（如 `vp_...`），会触发飞书 400。
- 定时任务是否执行取决于开关与 profile 覆盖值，详见 `开关手册.md`。
- **临时兜底映射（2026-06-07 起）**：当飞书回调中某用户的 `user_id` 为空时，系统按 `open_id` 自动映射临时 userId，使该用户可正常使用会议管理功能。命中时日志打印 `user_id empty, apply temp open_id fallback`。此映射为临时措施，待飞书侧修复用户身份问题后移除。

---

## 5. 常见问题排查

### 5.1 主持页看不到会序资料

按顺序检查：

1. `int_meeting.host_agenda` 是否有 `docs[]`。
2. 主持启动日志里 `docBoundTopics` 是否大于 0。
3. 当前会序是否配置了可访问飞书链接（`docx/wiki/base`）。

### 5.2 关闭待办后仍有提醒调用

- `meeting.todo.extraction-enabled` 只控制提取，不控制提醒调度。
- 需同时关闭 `meeting.todo.reminder-enabled`。

### 5.3 “会议管理”无弹窗

- 若日志出现 `Feishu send suppressed (AI host in-session)`，表示会中静音策略拦截了群卡片发送。

---

## 6. 运维最小建议

- 发布前确认当前 profile 的 `application-*.yml` 覆盖值。
- 涉及调度器开关变更后必须重启服务。
- 生产环境建议显式配置所有关键开关，避免依赖默认值。

---

## 7. 相关文档

- `README.md`：文档总入口。
- `开关手册.md`：开关全量说明与运维组合。

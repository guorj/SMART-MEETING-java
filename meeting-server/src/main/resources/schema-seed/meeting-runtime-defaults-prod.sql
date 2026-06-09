-- 幂等种子：会议核心业务开关出厂策略（生产向）
-- YAML 不再承载业务键；新环境/空库须执行本脚本后 Admin 热加载或重启 meeting-server。
-- Admin「恢复默认」= DELETE DB 行 → Java/Descriptor 出厂值（非本 seed 值）。
-- ON DUPLICATE KEY UPDATE 不覆盖 value_json，仅刷新 description / updated_at。

INSERT INTO int_meeting_system_config (config_key, category, value_json, description, updated_at)
VALUES
    -- AI 主持
    ('meeting.host.enabled', 'host', 'true', 'AI 主持总开关', NOW()),
    ('meeting.host.agenda-enabled', 'host', 'true', '会序推进（下一议题/跳过）', NOW()),
    ('meeting.host.tts-enabled', 'host', 'true', '主持话术 TTS 播报', NOW()),
    ('meeting.host.roll-call-enabled', 'host', 'true', '混合检点', NOW()),
    ('meeting.host.auto-roll-call-after-opening', 'host', 'true', '开场白后自动检点', NOW()),
    ('meeting.host.topic-timeout-strategy', 'host', '"REMIND_ONLY"', '议题超时策略', NOW()),
    ('meeting.host.roll-call.window-seconds', 'host', '12', '检点应答窗口（秒）', NOW()),
    ('meeting.host.roll-call.asr-grace-seconds', 'host', '6', '检点 ASR 定稿宽限（秒）', NOW()),
    ('meeting.host.roll-call.online-inventory-seconds', 'host', '60', '检点在线清单展示（秒）', NOW()),
    ('meeting.host.reminder.topic-minutes-left', 'host', '1', '议题剩余 N 分钟提醒', NOW()),
    ('meeting.host.reminder.meeting-minutes-left', 'host', '"10,3"', '整场剩余分钟提醒列表', NOW()),
    -- 会后纪要
    ('meeting.minute.generation-enabled', 'minute', 'true', '会后是否自动生成纪要', NOW()),
    ('meeting.minute.llm-enabled', 'minute', 'true', 'LLM 纪要初稿', NOW()),
    ('meeting.minute.ai-enhancement-enabled', 'minute', 'true', 'AI 增强', NOW()),
    ('meeting.minute.feishu-doc-enabled', 'minute', 'true', '飞书文档', NOW()),
    ('meeting.minute.notify-enabled', 'minute', 'true', '飞书通知', NOW()),
    ('meeting.minute.persist-enabled', 'minute', 'true', '库内持久化', NOW()),
    ('meeting.minute.expose-content-in-api', 'minute', 'true', 'API 暴露全文', NOW()),
    -- ASR
    ('meeting.asr.realtime-enabled', 'asr', 'false', '会中实时 ASR', NOW()),
    ('meeting.asr.offline-enabled', 'asr', 'true', '会后离线转写', NOW()),
    ('meeting.asr.offline-role-enabled', 'asr', 'true', '离线说话人分离', NOW()),
    ('meeting.asr.offline-role-mode', 'asr', '"auto"', '离线角色分离模式', NOW()),
    ('meeting.asr.offline-role-num-hint-enabled', 'asr', 'true', 'IST roleNum hint', NOW()),
    ('meeting.asr.offline-ist-max-role-num', 'asr', '10', 'IST roleNum 上限', NOW()),
    ('meeting.asr.offline-ist-max-feature-ids', 'asr', '64', 'IST featureIds 上限', NOW()),
    ('meeting.asr.offline-poll-max-retries', 'asr', '60', '离线 IST 轮询次数', NOW()),
    ('meeting.asr.offline-poll-interval-ms', 'asr', '5000', '离线 IST 轮询间隔 ms', NOW()),
    -- 待办
    ('meeting.todo.extraction-enabled', 'minute', 'false', '待办提取', NOW()),
    -- 流水线
    ('meeting.pipeline.pre-on-create-enabled', 'pipeline', 'false', '建会后立即 PRE', NOW()),
    ('meeting.pipeline.post-auto-trigger.enabled', 'pipeline', 'true', '会后 POST 流水线', NOW()),
    ('meeting.pipeline.post-auto-trigger.template-code', 'pipeline', '""', 'POST 模板编码', NOW()),
    -- 会前调度（scan-ms 为 infra，不在此表）
    ('meeting.scheduler.pre-enabled', 'scheduler', 'false', '会前 PRE 自动调度', NOW()),
    ('meeting.scheduler.pre-window-minutes', 'scheduler', '5', '会前触发容忍窗口（分钟）', NOW()),
    ('meeting.scheduler.pre-24h-template-code', 'scheduler', '""', '会前 24h 模板', NOW()),
    ('meeting.scheduler.pre-10m-template-code', 'scheduler', '""', '会前 10min 模板', NOW()),
    -- 通知
    ('meeting.notification.bot-enabled', 'notification', 'false', 'Bot 中转通知', NOW()),
    ('meeting.notification.fallback-direct', 'notification', 'true', 'Bot 失败回退直连飞书', NOW()),
    -- 声纹标注 / ISV（Admin 展示归入 asr 树）
    ('meeting.voiceprint.offline-label-enabled', 'asr', 'true', '离线 ISV 声纹标注', NOW()),
    ('meeting.voiceprint.offline-min-slice-ms', 'asr', '3000', 'ISV 切片最小时长 ms', NOW()),
    ('meeting.voiceprint.offline-max-slice-ms', 'asr', '10000', 'ISV 切片最大时长 ms', NOW()),
    ('meeting.voiceprint.offline-max-speakers', 'asr', '20', '单场 ISV 处理 IST 簇上限', NOW()),
    ('meeting.voiceprint.offline-vote-slices', 'asr', '3', '簇级 ISV 投票句段数', NOW()),
    ('meeting.voiceprint.offline-segment-relabel-enabled', 'asr', 'true', '未命名簇逐段补标', NOW()),
    ('meeting.voiceprint.offline-split-cluster-enabled', 'asr', 'true', 'IST 簇内分裂', NOW()),
    ('meeting.voiceprint.offline-split-min-segments', 'asr', '2', '簇分裂最少句段数', NOW()),
    ('meeting.voiceprint.offline-min-slice-floor-ms', 'asr', '1000', '切片时长下限保护 ms', NOW()),
    -- ISV
    ('meeting.isv.enabled', 'asr', 'true', 'ISV 声纹 1:N', NOW()),
    ('meeting.isv.match-score-threshold', 'asr', '0.2', '1:N 匹配最低置信度', NOW()),
    ('meeting.isv.search-top-k-max', 'asr', '10', 'ISV topK 上限', NOW()),
    ('meeting.isv.search-top-k-min', 'asr', '3', 'ISV topK 下限', NOW()),
    ('meeting.isv.min-slice-bytes', 'asr', '1600', 'ISV 最小 PCM 字节', NOW()),
    ('meeting.isv.min-segment-ms-for-slice', 'asr', '500', 'ISV 句段最小时长 ms', NOW()),
    -- Web
    ('meeting.web.static-cache-seconds', 'web', '300', '静态资源缓存秒数', NOW()),
    ('meeting.web.page-cache-buster', 'web', '""', '录音/主持页 URL 缓存破除', NOW()),
    -- OpenClaw
    ('openclaw.enabled', 'openclaw', 'true', 'OpenClaw 增强', NOW()),
    ('openclaw.skill-mode', 'openclaw', 'true', 'OpenClaw Skill 模式', NOW()),
    ('openclaw.timeout-seconds', 'openclaw', '60', 'OpenClaw invoke 超时（秒）', NOW()),
    ('openclaw.max-concurrent-invokes', 'openclaw', '5', 'OpenClaw 并行 invoke 上限', NOW())
ON DUPLICATE KEY UPDATE
    description = VALUES(description),
    updated_at = VALUES(updated_at);

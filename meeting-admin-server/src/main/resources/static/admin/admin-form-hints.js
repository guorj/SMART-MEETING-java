/**
 * 管理后台表单字段说明（各模块共用）。
 * 用法：AdminForm.field('标签', '<input .../>', AdminHints.pushBot.taskName)
 */
window.AdminForm = {
  hint: function (text) {
    return text ? '<p class="form-hint">' + text + '</p>' : '';
  },
  field: function (label, controlHtml, hintText) {
    return '<div class="form-field"><label>' + label + '</label>' + controlHtml + this.hint(hintText) + '</div>';
  },
  /** select 变更时同步更新 hint 段落 */
  bindSelectHint: function (selectId, hintId, hintMap, fallback) {
    var sel = document.getElementById(selectId);
    var hintEl = document.getElementById(hintId);
    if (!sel || !hintEl) return;
    var sync = function () {
      var v = sel.value;
      hintEl.textContent = hintMap[v] || fallback || '';
    };
    sel.addEventListener('change', sync);
    sync();
  }
};

window.AdminHints = {
  pushBot: {
    moduleIntro: '管理 feishu-scheduled-bot 的飞书推送任务：Cron 定时或外部触发，向用户/群发送消息。保存后点「同步 Quartz」使 INTERNAL 任务的 Cron 生效。',
    syncQuartz: '向 bot 发送 reload-schedule，重新加载所有 enabled 且 scheduleMode=INTERNAL 且有 Cron 的任务到 Quartz。',
    taskName: '任务显示名，列表与日志中展示；必填。',
    cron: 'Quartz Cron（5 或 6 段，如 0 30 9 * * MON-FRI = 工作日 9:30）。INTERNAL 模式且任务启用时用于自动触发；EXTERNAL 可留空。点「预览」查看下次触发时间。',
    scheduleMode: {
      INTERNAL: 'INTERNAL — bot 内 Quartz 按 Cron 自动推送。任务须「启用」且填写 Cron 才会注册定时器。管理台「执行」：全局 feishu.schedule.mode=both 且 bot 配置 allow-internal-manual-execute=false 时会被拒绝(409)；默认允许手动试发。',
      EXTERNAL: 'EXTERNAL — 不注册 Quartz Cron，仅由外部系统或本页「执行」、POST /api/tasks/{id}/execute 触发。每次手动执行都会真实推送（无 DUPLICATE 去重）。'
    },
    targetType: {
      USER: 'USER — 推送给单个飞书用户，目标 ID 填飞书 user_id（企业内用户 ID）。',
      GROUP: 'GROUP — 推送到群聊，目标 ID 填 chat_id（oc_…）。'
    },
    targetId: '飞书 user_id（单聊）或 chat_id（群聊），须与「目标类型」一致。',
    message: '推送正文（纯文本或卡片 JSON，依 bot 配置）；必填。',
    skipHolidays: '启用后，法定节假日当天 Cron 触发会跳过（仍可通过「执行」手动试发，受日期过滤规则约束）。',
    enabled: '关闭后任务不参与 Quartz 调度，也不会被 Cron 自动触发；EXTERNAL 任务仍可手动「执行」。',
    logMeetingId: '按会议 ID 筛选推送日志（meeting-server 触发的推送会带上 meetingId）。',
    logTaskId: '按 push_task 主键筛选。',
    logStatus: 'SUCCESS=成功；FAILED=失败；SKIPPED=跳过（如假日、去重等）。',
    skipReasonDuplicate: 'DUPLICATE — 旧版 bot 对 /execute 有当日去重；升级并重启 feishu-scheduled-bot 后，手动「执行」不再 DUPLICATE。若仍出现，说明 bot 未部署新代码。'
  },
  weeklyJobs: {
    moduleIntro: '周报/事项对比定时任务：按 Cron 拉取纪要、对比源资料、写入 OUTPUT 飞书文档。保存后会自动请求同步 Quartz；也可手动点「同步 Quartz」。',
    jobName: '任务唯一标识名，日志与 Quartz JobKey 使用；必填。',
    enabled: '关闭后不注册 Cron，也不会被定时触发；仍可「执行」立即跑一轮。',
    cron: 'Quartz Cron，默认每周一 10:00（0 10 * * MON）。时区沿用任务 scheduleTimezone（默认 Asia/Shanghai）。',
    minuteQueryType: {
      PRESET_LAST_7_DAYS: 'PRESET_LAST_7_DAYS — 按会务类型 presetTypeCode 查询最近 N 天已结束会议的纪要作为对比输入。',
      MEETING_IDS: 'MEETING_IDS — 仅使用 minute_query_params 中指定的 meetingId 列表。'
    },
    minuteQueryParams: 'JSON 参数。PRESET_LAST_7_DAYS 示例：{"presetTypeCode":1,"days":7}。MEETING_IDS 示例：{"meetingIds":["uuid1","uuid2"]}。',
    sourceConfigNames: '源资料 configName，逗号分隔；须与会务预设 host_agenda 中 SOURCE/BOTH 绑定名一致，用于读取对比基准文档。',
    outputConfigName: '产出 configName；须为 host_agenda 中 OUTPUT/BOTH 的绑定名，对比结果写入对应飞书文档/文件夹。'
  },
  presets: {
    presetCode: '会务类型编号 1–5，对应 int_meeting_type_preset；切换后加载该类型的会序与资料绑定。',
    agendaTitle: '会序项标题，主持页与议程展示用；修改后自动保存。',
    agendaMinutes: '预计时长（分钟），用于会序计时与汇总。',
    agendaOwners: '会序负责人（飞书 user_id），支持多个，逗号分隔；流水线会据此自动分发回填任务。',
    configName: '资料配置名，weekly-jobs 的 source/output 引用此名称；建议 preset{N}- 前缀便于识别。',
    configRole: {
      SOURCE: 'SOURCE — 只读源资料，供事项对比、AI 读取输入。',
      OUTPUT: 'OUTPUT — 对比/生成结果的写入目标。',
      BOTH: 'BOTH — 同时作为源与产出（读写同一文档时使用）。'
    },
    resourceSlot: '同一议题下多份资料的排序槽位，从 0 递增；保存时自动去重。',
    bitableDisplayMode: {
      '': '默认 — 按 bot/meeting-server 内置规则解析多维表。',
      RAW: 'RAW — 扁平行列表，不做分组。',
      GROUPED: 'GROUPED — 按业务字段分组展示（适合周报类表格）。'
    },
    feishuUrl: '飞书文档或多维表完整 URL；保存后 meeting-server 可 enrich 快照。',
    localUpload: '本地上传：支持 doc/docx 与 jpg/png/gif/webp，可多选；每个文件生成一条资料绑定，上传后在卡片内预览，主持页会中可查看图片或下载文档。',
    hostAgendaJson: 'host_agenda v2 原始 JSON；直接编辑不会自动维护 bindings 索引，仅适合高级运维。'
  },
  meetings: {
    moduleIntro: '查询与运维进行中的会议实例；可强制结束异常会议，并从详情跳转该会议的推送日志。',
    status: '按生命周期筛选：ISSUE_COLLECTING/INVITED/STARTED/RECORDING/PROCESSING/COMPLETED/PAUSED/CANCELLED。',
    preset: '按会务类型 presetTypeCode（1–5）筛选。',
    forceEnd: '强制结束会议并清理进行中状态；操作不可撤销，请确认后再点。'
  },
  settings: {
    moduleIntro: '覆盖 meeting-server 运行时开关（写入 DB 后可热加载）。「恢复默认」删除 DB 覆盖并回退 YAML 默认值。',
    reloadRuntime: '通知 meeting-server 重新加载 int_meeting_system_config，无需重启进程。',
    audit: '查看最近配置变更记录（谁改了什么、旧值/新值）。',
    'meeting.host.enabled': '总开关：关闭后主持 Agent 不参与会议流程（与 YAML meeting.host.enabled 可被 DB 覆盖）。',
    'meeting.host.agenda-enabled': '是否允许 AI 推进会序（下一议题、跳过等）；关闭后会序仍展示但不自动推进。',
    'meeting.host.roll-call-enabled': '是否启用混合检点流程（点名确认参会）。',
    'meeting.host.auto-roll-call-after-opening': '开场白结束后自动进入检点；需会序含检点关键词且 roll-call-enabled=true。',
    'meeting.host.tts-enabled': '主持语音 TTS 播报；关闭后仅文字提示。'
  },
  integrations: {
    moduleIntro: '三进程部署入口与健康探测；推送/对比任务请在对应模块配置。',
    health: '探测 feishu-scheduled-bot 是否可达（MEETING_NOTIFY_BOT_URL + SCHEDULED_BOT_APIKEY）。',
    reverseProxy: '生产环境经 Nginx 反代时，bot context-path 通常为 /scheduled-bot，meeting-server 为 /meeting-server。'
  },
  observability: {
    moduleIntro: '按会议 ID 只读查看已生成纪要与 ASR 转写片段，用于排障与内容抽检。',
    meetingId: '会议 UUID，可从「会议列表」详情 JSON 中复制 id 字段。'
  },
  apiReference: {
    moduleIntro: '数据更新与运维 API 目录（Admin Token 鉴权）；可筛选分类、搜索路径，复制 cURL 做联调。'
  },
  users: {
    moduleIntro: '按 OA userId 统一管理用户档案：一行聚合 int_user_mapping_feishu（飞书映射）与 int_voiceprint（声纹）。后续新增用户相关表可在此模块扩展。',
    editorIntro: '保存时同时写入映射表与声纹表；列表一行即该用户的完整运维视图。',
    userId: 'OA 系统用户 ID（主键）；与参会人、待办责任人等业务侧 user_id 一致。新建后不可修改。',
    userName: '用户姓名，列表展示与纪要人名匹配用；必填。',
    feishuUserId: '飞书企业内 user_id（唯一身份）；推送个人消息、待办 @、声纹注册均使用此字段。',
    featureIdOptional: '讯飞声纹 featureId；留空则只维护映射、不创建/更新声纹。',
    groupId: '讯飞声纹组 ID；可留空。',
    registeredAt: '声纹注册时间；留空则默认当前时间。',
    expiresAt: '声纹过期时间；留空则默认注册时间 + 10 年。',
    expiryFilter: '按主声纹（同 userId 最新一条）的过期状态筛选列表。',
    clearVoiceprint: '勾选后保存将删除该用户全部声纹记录（映射保留）。'
  }
};

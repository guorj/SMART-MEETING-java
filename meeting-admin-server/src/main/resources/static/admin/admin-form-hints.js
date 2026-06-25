(function () {
  'use strict';

  function settingsProxy(base) {
    return new Proxy(base, {
      get: function (target, prop) {
        if (prop in target) return target[prop];
        if (typeof prop === 'string') return '';
        return undefined;
      }
    });
  }

  window.AdminHints = {
    users: {
      moduleIntro: '飞书用户映射、前台授权与声纹关联。',
      editorIntro: '编辑 OA 用户与飞书映射；声纹字段可选，留空表示不绑定。',
      userId: 'OA 系统 userId，须为正整数且全局唯一。',
      userName: '显示姓名，用于后台列表与会议参会展示。',
      feishuUserId: '飞书 open_id / user_id，用于机器人推送与前台令牌映射。',
      featureId: '声纹特征 ID（讯飞），与 groupId 配套。',
      featureIdOptional: '可选；填写后须同时维护 groupId 与有效期。',
      groupId: '声纹库分组 ID。',
      registeredAt: '声纹注册时间（本地时区）。',
      expiresAt: '声纹过期时间；过期后纪要说话人识别可能降级。',
      clearVoiceprint: '勾选后保存将清空该用户声纹绑定。',
      expiryFilter: '按声纹有效期筛选列表。',
      dashboardGrantHint: '为飞书用户配置前台权限：是否可创建/结束会议、注册声纹等。',
      dashboardGrantNeedFeishu: '需先填写 feishu_user_id 后才能授权前台能力。'
    },
    dashboardGrants: {
      defaultDeny: '开启后，未显式授权的用户默认无法使用前台能力。',
      enabled: '启用前台授权后，用户可从飞书「会议管理」进入工作台。',
      canCreateMeeting: '允许创建会议并进入主持/录音页。',
      canEndMeeting: '允许结束进行中的会议并触发纪要流程。',
      canRegisterVoiceprint: '允许打开声纹注册页。',
      remark: '备注仅管理员可见。'
    },
    presets: {
      moduleIntro: '维护会务类型预设、会序与资料绑定；资料角色影响 weekly-jobs 引用。',
      presetCode: '会务类型编号，对应 int_meeting_type_preset.preset_type_code。',
      agendaTitle: '会序标题，支持拖拽排序；修改后自动保存。',
      agendaMinutes: '单项会序预计时长（分钟）。',
      agendaOwners: '负责人飞书 user_id，多个用英文逗号分隔。',
      hostAgendaJson: 'host_agenda_json 原始 JSON；仅在高级模式手工编辑。',
      scheduleConfig: 'schedule_config 机器可读排期（weekly/fixed/at_start）；驱动 Dashboard 快速开始的 scheduled_time。schedule_note 仅人读展示。',
      configName: '资料 config_name，须与会务预设内唯一；weekly-jobs 按此引用。',
      resourceSlot: '资料槽位序号，同会序内从 0 递增。',
      feishuUrl: '飞书 docx / bitable / sheet 链接；保存后用于会中拉取与对比任务。',
      oabpTaskSql: '可选。oabp 库只读 SELECT（单条、无分号），会中追加「项目任务」表格；须 meeting-server 启用 MEETING_DB_OABP_ENABLED。',
      localUpload: '支持 doc/docx、ppt/pptx、pdf、xls/xlsx、csv 与常见图片；ppt/pdf 单文件最大 50MB，可多选；旧版 .ppt 建议转为 pptx 以便会中幻灯片预览。',
      configRole: {
        SOURCE: 'SOURCE：可作为周报对比源资料。',
        OUTPUT: 'OUTPUT：可作为对比产出文档。',
        BOTH: 'BOTH：同时可作为源与产出。'
      },
      bitableDisplayMode: {
        '': '默认：按字段类型自动排版。',
        RAW: 'RAW：表格原始行列。',
        GROUPED: 'GROUPED：按分组字段聚合展示。'
      }
    },
    weeklyJobs: {
      moduleIntro: '配置事项进度周报对比任务，按 Cron 定时执行。',
      jobName: '任务显示名，便于运维识别。',
      enabled: '关闭后不参与 Quartz 调度。',
      cron: 'Quartz Cron 表达式，例如 0 10 * * MON 表示每周一 10:00。',
      minuteQueryType: {
        PRESET_LAST_7_DAYS: '按会务类型查询最近 N 天已结束会议的纪要。',
        MEETING_IDS: '按指定会议 ID 列表查询纪要。'
      },
      minuteQueryParams: 'JSON 参数：PRESET_LAST_7_DAYS 用 {"presetTypeCode":1,"days":7}；MEETING_IDS 用 {"meetingIds":["id1"]}。',
      sourceConfigNames: '源资料 config_name，多个用英文逗号分隔，须已在会序资料中标记 SOURCE/BOTH。',
      outputConfigName: '产出 config_name，须已标记 OUTPUT/BOTH。'
    },
    pushBot: {
      moduleIntro: '管理 INTERNAL/EXTERNAL 推送任务与发送日志。',
      taskName: '任务显示名。',
      cron: 'INTERNAL 模式下的 Quartz Cron；EXTERNAL 可留空。',
      scheduleMode: {
        INTERNAL: '服务端按 Cron 自动触发推送。',
        EXTERNAL: '仅外部系统或「执行」按钮手动触发。'
      },
      targetType: {
        USER: '单聊用户：targetId 填飞书 user_id。',
        GROUP: '群聊：targetId 填 chat_id。'
      },
      targetId: '飞书 user_id 或 chat_id。',
      message: '推送正文，支持飞书卡片占位符（若模板需要）。',
      skipHolidays: '开启后节假日跳过 INTERNAL 调度。',
      enabled: '关闭后任务不参与 Cron。',
      syncQuartz: '将数据库任务同步到 Quartz 调度器。',
      logTaskId: '按推送任务 ID 筛选日志。',
      logMeetingId: '按关联会议 ID 筛选日志。'
    },
    meetings: {
      moduleIntro: '会议运维：列表筛选、强制结束；数据查看聚合纪要/转写/推送/流水线。',
      status: '按会议生命周期状态筛选。',
      preset: '按会务类型 presetTypeCode 筛选。',
      forceEnd: '强制结束进行中的会议并进入纪要流程（慎用）。'
    },
    observability: {
      moduleIntro: '按会议 ID 查看纪要、转写与关联数据。',
      meetingId: '会议 UUID 或业务 ID。'
    },
    integrations: {
      moduleIntro: '三进程入口、健康探测与运维快捷链接。',
      health: '探测 feishu-scheduled-bot 可达性与 meeting-server 桥接配置。',
      reverseProxy: '生产环境通常经 Nginx 反代；链接以实际部署域名为准。'
    },
    settings: settingsProxy({
      moduleIntro: '系统参数热更新；可热加载项保存后需点「通知 meeting-server 热加载」。',
      reloadRuntime: '通知 meeting-server 从 DB 重载可热更配置。',
      audit: '查看最近配置变更审计记录。'
    }),
    apiReference: {
      moduleIntro: '管理端 Open API 目录，可复制路径用于联调。'
    },
    pipeline: {
      moduleIntro: '按 PRE/MID/POST 配置流水线模板与步骤。'
    }
  };
})();

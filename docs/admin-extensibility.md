# 管理后台扩展开发说明

## L1：新增 Admin 模块

1. 实现 `com.smartmeeting.config.admin.AdminModule` 并 `@Component`
2. 新增 `*AdminController`，路径与 `apiPathPrefix()` 一致
3. 新增 `static/admin/modules/xxx.js`，`AdminModules.register({ route, mount })`
4. 在 `application.yml` 的 `meeting.admin.modules` 中加入模块 ID（可选白名单）

参考：`DemoAdminModule`（默认 disabled）。

## L2：新增会序配置源

1. 实现 `AgendaConfigProvider`（`providerId`、`order`）
2. Spring 自动注入 `AgendaConfigProviderRegistry`
3. `GET /api/v1/admin/agenda-config/providers` 可排查已注册列表

**合并逻辑（单一事实来源）**：`meeting-config-core` 的 `PresetAgendaMergeEngine` + `FeishuResourceResolver`。  
`meeting-server` 的 `PresetAgendaDocService` 仅负责 DB/Redis 加载与飞书正文 HTTP；禁止在 server 内重复实现 enrich / resolve 规则。

已内置 **`FileBasedAgendaConfigProvider`**（`providerId=file_based`，默认关闭）：

```yaml
meeting:
  admin:
    agenda-config:
      file-enabled: true
      file-path: classpath:agenda-templates/example-presets.json
```

示例文件：`meeting-admin-server/src/main/resources/agenda-templates/example-presets.json`（只读）。

## L3：新增系统参数

1. 实现 `SystemConfigDescriptor` 并 `@Component`
2. 在 `MeetingRuntimeConfigLoader`（meeting-server）增加 key 映射
3. Settings 页通过 `/api/v1/admin/system-config/schema` 自动生成表单项

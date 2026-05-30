# 会议管理后台 P2

## meeting-server Internal API（`X-Internal-Token`）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/internal/runtime-config/reload` | 热加载系统参数 |
| POST | `/api/v1/internal/meetings/refresh-host-agenda` | 批量刷新未开会 `host_agenda`（**含飞书 enrich**） |
| POST | `/api/v1/internal/presets/{code}/refresh-cache` | 刷新 preset Redis 缓存 |
| POST | `/api/v1/internal/presets/refresh-cache-all` | 刷新 preset 1～5 |

`refresh-host-agenda` 请求体示例：

```json
{
  "presetTypeCode": 1,
  "dryRun": true,
  "meetingIds": []
}
```

admin-server 通过 `MeetingServerBridgeService` 调用上述接口。

## 飞书 OAuth 登录（可选）

默认关闭静态 Token 登录；启用后：

```yaml
meeting:
  admin:
    oauth:
      feishu:
        enabled: true
        app-id: ${FEISHU_APP_ID}
        app-secret: ${FEISHU_APP_SECRET}
        redirect-uri: http://127.0.0.1:8766/admin/oauth/feishu/callback
        allowed-user-ids:   # 非空则白名单
          - ou_xxx
```

流程：`/admin/oauth/feishu/start` → 飞书授权 → 回调签发 session token → 存入浏览器 `sessionStorage`（与 `X-Admin-Token` 同头传递）。

## 集成入口模块

侧栏 **集成入口**：汇总 meeting-server、feishu-bot 链接与反代提示。

## 会前流程编排（零基础入口）

P2 保持与当前后台能力一致：会前流程可由运营/会务在后台配置，不要求写代码。  
请直接参考：

- [会前流程编排指南-零基础.md](会前流程编排指南-零基础.md)

推荐策略（与当前实现一致）：

1. 启用 PRE 自动触发（24h / 10min 双模板）
2. 启用 POST 自动触发（会议结束后）
3. MID 继续手动触发（按业务要求暂不自动化）

生产可选 Nginx 片段（示意）：

```nginx
location /admin/ {
  proxy_pass http://127.0.0.1:8766/admin/;
}
location /api/v1/admin/ {
  proxy_pass http://127.0.0.1:8766/api/v1/admin/;
}
# meeting-server 使用 context-path=/meeting-server；内网桥接 http://127.0.0.1:8765/meeting-server
location /meeting-server/ {
  proxy_pass http://127.0.0.1:8765;
}
```

## 启用检查清单

1. `INTERNAL_RELOAD_TOKEN` 在 admin-server 与 meeting-server 一致
2. `MEETING_SERVER_URL` 指向可访问的 meeting-server
3. 执行刷新前 meeting-server 已启动且能连 MySQL/Redis/飞书

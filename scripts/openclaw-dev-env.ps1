# 本地开发 OpenClaw 环境变量（配合 scripts/openclaw-tunnel.ps1）
# 用法：在启动 meeting-server 前执行
#   . .\scripts\openclaw-dev-env.ps1

$env:OPENCLAW_GATEWAY_URL = "http://127.0.0.1:18789"
if (-not $env:OPENCLAW_AUTH_TOKEN) {
    $env:OPENCLAW_AUTH_TOKEN = "49a4395d662298ec7c4efa2d038411ae3aceb41bd4912271"
}
Remove-Item Env:OPENCLAW_DEVICE_TOKEN -ErrorAction SilentlyContinue

Write-Host "OPENCLAW_GATEWAY_URL=$env:OPENCLAW_GATEWAY_URL" -ForegroundColor Green
Write-Host "请确认 scripts/openclaw-tunnel.ps1 已在另一窗口运行。" -ForegroundColor Yellow

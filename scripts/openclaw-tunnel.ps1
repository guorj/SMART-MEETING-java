# OpenClaw Gateway SSH 隧道（Windows 本地开发）
# 将阿里云 39.97.61.212:18789 映射到本机 127.0.0.1:18789，meeting-server 用 loopback 模式获得 operator scopes。
#
# 用法（PowerShell，保持窗口不关）：
#   .\scripts\openclaw-tunnel.ps1
#
# meeting-server dev 配置应保持：
#   OPENCLAW_GATEWAY_URL=http://127.0.0.1:18789
#   OPENCLAW_AUTH_TOKEN=<与 Gateway gateway.auth.token 一致>

param(
    [string]$RemoteHost = "39.97.61.212",
    [string]$RemoteUser = "root",
    [int]$LocalPort = 18789,
    [int]$RemotePort = 18789
)

$ErrorActionPreference = "Stop"

function Resolve-SshIdentity {
    param([string]$SourceKey)
    if (-not (Test-Path $SourceKey)) {
        throw "找不到 SSH 私钥: $SourceKey"
    }
    $icacls = icacls $SourceKey 2>&1 | Out-String
    if ($icacls -notmatch "LOCAL SERVICE|Users:\(I\)\(RX\)|Authenticated Users") {
        return $SourceKey
    }
    $tempKey = Join-Path $env:TEMP "openclaw_ssh_id_rsa"
    Copy-Item $SourceKey $tempKey -Force
    icacls $tempKey /inheritance:r | Out-Null
    icacls $tempKey /grant:r "${env:USERNAME}:(R)" | Out-Null
    Write-Host "已复制私钥到临时文件（原密钥 ACL 过宽，OpenSSH 无法直接使用）: $tempKey" -ForegroundColor Yellow
    return $tempKey
}

$identity = Resolve-SshIdentity (Join-Path $env:USERPROFILE ".ssh\id_rsa")

Write-Host "OpenClaw SSH tunnel: 127.0.0.1:${LocalPort} -> ${RemoteHost}:${RemotePort}" -ForegroundColor Cyan
Write-Host "保持本窗口运行；另开终端启动 meeting-server（profile=dev）。" -ForegroundColor Yellow
Write-Host "环境变量: OPENCLAW_GATEWAY_URL=http://127.0.0.1:${LocalPort}" -ForegroundColor Yellow
Write-Host ""

ssh -i $identity -o StrictHostKeyChecking=accept-new -N -L "${LocalPort}:127.0.0.1:${RemotePort}" "${RemoteUser}@${RemoteHost}"

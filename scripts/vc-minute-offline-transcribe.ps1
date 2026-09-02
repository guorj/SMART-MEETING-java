#Requires -Version 5.1
<#
.SYNOPSIS
  飞书 VC 妙记 → 下载 → 讯飞离线全文转写（标准操作 Windows 入口）

.EXAMPLE
  .\scripts\vc-minute-offline-transcribe.ps1 "https://xxx.feishu.cn/minutes/obcnl8h3oys41512bprgtq63"

.EXAMPLE
  .\scripts\vc-minute-offline-transcribe.ps1 obcnl8h3oys41512bprgtq63 --skip-download
#>
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$MinuteUrlOrToken,

    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$ExtraArgs
)

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Push-Location $ScriptDir
try {
    if (-not (Test-Path "node_modules")) {
        Write-Host "Installing scripts dependencies (mysql2)..."
        npm install --no-fund --no-audit 2>&1 | Out-Null
    }
    $argsList = @($MinuteUrlOrToken) + $ExtraArgs
    & node ".\vc-minute-offline-transcribe.mjs" @argsList
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} finally {
    Pop-Location
}

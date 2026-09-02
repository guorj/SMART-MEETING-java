# 修复 Windows 上 OpenSSH 私钥权限（需管理员 PowerShell）
# 解决: WARNING: UNPROTECTED PRIVATE KEY FILE / Permission denied (publickey)
#
# 用法：右键「以管理员身份运行」PowerShell，然后：
#   cd D:\openclaw\workspace-clone\projects\smart-meeting-java
#   .\scripts\fix-ssh-key-permissions.ps1

$ErrorActionPreference = "Stop"

$key = Join-Path $env:USERPROFILE ".ssh\id_rsa"
if (-not (Test-Path $key)) {
    Write-Error "找不到 $key"
}

Write-Host "修复 SSH 私钥权限: $key" -ForegroundColor Cyan

# 重置 ACL，仅当前用户可读
icacls $key /inheritance:r | Out-Null
icacls $key /grant:r "$($env:USERNAME):(R)" | Out-Null
icacls $key /remove "BUILTIN\Users" "NT AUTHORITY\Authenticated Users" "NT AUTHORITY\LOCAL SERVICE" "NT AUTHORITY\NETWORK SERVICE" "APPLICATION PACKAGE AUTHORITY\ALL APPLICATION PACKAGES" "APPLICATION PACKAGE AUTHORITY\所有受限制的应用程序包" 2>$null | Out-Null

Write-Host "当前 ACL:" -ForegroundColor Green
icacls $key

Write-Host ""
Write-Host "测试 SSH:" -ForegroundColor Cyan
ssh -o BatchMode=yes -o ConnectTimeout=8 root@39.97.61.212 "echo ok"

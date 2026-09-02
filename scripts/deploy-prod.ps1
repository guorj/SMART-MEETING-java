# 生产部署 — 从开发机打包上传到阿里云 39.97.61.212
# Usage (PowerShell, 项目根目录):
#   .\scripts\deploy-prod.ps1
#
# 前提: SSH 密钥 $env:USERPROFILE\.ssh\id_rsa_deploy 可连 root@39.97.61.212

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $Root

$Remote = "root@39.97.61.212"
$SshKey = "$env:USERPROFILE\.ssh\id_rsa_deploy"
$RemoteDir = "/root/smart-meeting-java"

Write-Host "==> Maven package (skip tests)"
& .\mvnw.cmd -pl meeting-server,meeting-admin-server -am package -DskipTests
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$ServerJar = Join-Path $Root "meeting-server\target\meeting-server-0.1.0.jar"
$AdminJar = Join-Path $Root "meeting-admin-server\target\meeting-admin-server-0.1.0.jar"
$StartScript = Join-Path $Root "scripts\start-prod.sh"

foreach ($f in @($ServerJar, $AdminJar, $StartScript)) {
    if (-not (Test-Path $f)) { throw "Missing: $f" }
}

Write-Host "==> Upload JARs + start script"
& scp -o BatchMode=yes -o IdentitiesOnly=yes -i $SshKey `
    $ServerJar, $AdminJar, $StartScript `
    "${Remote}:${RemoteDir}/"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "==> Fix nginx + launch on server"
& scp -o BatchMode=yes -o IdentitiesOnly=yes -i $SshKey `
    (Join-Path $Root "scripts\remote-prod-setup.sh") `
    "${Remote}:/tmp/remote-prod-setup.sh"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

& ssh -o BatchMode=yes -o IdentitiesOnly=yes -i $SshKey $Remote `
    "sed -i 's/\r$//' /tmp/remote-prod-setup.sh; bash /tmp/remote-prod-setup.sh"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "==> Verify public health"
curl.exe -s "https://oa.qdyhjz.cn/meeting-server/actuator/health"
Write-Host ""

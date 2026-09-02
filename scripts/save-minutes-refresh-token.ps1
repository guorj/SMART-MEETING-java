param(
    [Parameter(Mandatory = $true)]
    [string]$RefreshToken
)

$headers = @{ 'X-Admin-Token' = 'dev-admin-token'; 'Content-Type' = 'application/json' }
$body = @{ valueJson = ('"' + $RefreshToken + '"') } | ConvertTo-Json -Compress
$r = Invoke-RestMethod -Method Put `
    -Uri 'https://oa.qdyhjz.cn/meeting-admin/api/v1/admin/system-config/entries/meeting.feishu.minutes.user-refresh-token' `
    -Headers $headers -Body $body
$r | ConvertTo-Json -Depth 3

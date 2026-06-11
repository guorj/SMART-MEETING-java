$src = Join-Path $PSScriptRoot '*'
$targets = @(
  (Join-Path $PSScriptRoot '..\meeting-server\src\main\resources\static\ui-kit'),
  (Join-Path $PSScriptRoot '..\meeting-admin-server\src\main\resources\static\ui-kit')
)
foreach ($t in $targets) {
  if (Test-Path $t -PathType Leaf) {
    Remove-Item $t -Force
  }
  New-Item -ItemType Directory -Force -Path $t | Out-Null
  Copy-Item $src $t -Recurse -Force
  Write-Host "Synced ui-kit -> $t"
}

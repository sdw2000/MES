$ErrorActionPreference = 'Stop'
$exe = 'C:\tools\frp\frpc.exe'
$ini = 'C:\tools\frp\frpc.ini'

if (!(Test-Path $exe)) {
  Write-Host "frpc.exe not found: $exe"
  exit 2
}
if (!(Test-Path $ini)) {
  Write-Host "frpc.ini not found: $ini"
  exit 3
}

$exists = Get-Process frpc -ErrorAction SilentlyContinue | Select-Object -First 1
if ($exists) {
  Write-Host "frpc already running pid=$($exists.Id)"
  exit 0
}

Start-Process -FilePath $exe -ArgumentList @('-c', $ini) -WindowStyle Hidden
Start-Sleep -Seconds 2

$started = Get-Process frpc -ErrorAction SilentlyContinue | Select-Object -First 1
if ($started) {
  Write-Host "frpc started pid=$($started.Id)"
  exit 0
}

Write-Host "frpc start failed"
exit 1

Stop-Process -Id 44892 -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 1
Start-Process powershell -ArgumentList @('-NoProfile','-ExecutionPolicy','Bypass','-File','E:\vue\ERP\tools\bartender\start-gateway.ps1') -WorkingDirectory 'E:\vue\ERP\tools\bartender' -WindowStyle Hidden
Start-Sleep -Seconds 3
Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:9123/health' -TimeoutSec 5 | Select-Object -ExpandProperty StatusCode
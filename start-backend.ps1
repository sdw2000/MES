# MES backend start script
# Usage:
#   .\start-backend.ps1
#   .\start-backend.ps1 -Restart

param(
    [switch]$Restart
)

Set-Location $PSScriptRoot

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "   MES backend start script" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "1. Check Redis..." -ForegroundColor Yellow
try {
    $redisConn = Get-NetTCPConnection -LocalPort 6379 -ErrorAction SilentlyContinue
    if (-not $redisConn) {
        $redisServerCmd = Get-Command redis-server -ErrorAction SilentlyContinue
        if (-not $redisServerCmd) {
            Write-Host "   redis-server not found in PATH" -ForegroundColor Red
            Write-Host "   Please install Redis or set PATH" -ForegroundColor Yellow
            exit 1
        }
        Write-Host "   Redis not running, trying to start by redis-server..." -ForegroundColor Yellow
        Start-Process -FilePath $redisServerCmd.Source -WindowStyle Hidden
        Start-Sleep -Seconds 2
    }

    $redisCliCmd = Get-Command redis-cli -ErrorAction SilentlyContinue
    if ($redisCliCmd) {
        $redisTest = & $redisCliCmd.Source -h 127.0.0.1 -p 6379 ping 2>$null
        if ($redisTest -ne "PONG") {
            Write-Host "   Redis ping failed" -ForegroundColor Red
            exit 1
        }
    } else {
        $redisConn = Get-NetTCPConnection -LocalPort 6379 -ErrorAction SilentlyContinue
        if (-not $redisConn) {
            Write-Host "   Redis port 6379 not available" -ForegroundColor Red
            exit 1
        }
    }
    Write-Host "   Redis is running" -ForegroundColor Green
} catch {
    Write-Host "   Failed to connect Redis" -ForegroundColor Red
    Write-Host "   Please make sure Redis is started" -ForegroundColor Yellow
    exit 1
}

Write-Host ""
Write-Host "2. Check application package..." -ForegroundColor Yellow
$jarPath = "target\MES-0.0.1-SNAPSHOT.jar"
if (Test-Path $jarPath) {
    $jarInfo = Get-Item $jarPath
    Write-Host "   Found: $($jarInfo.Name)" -ForegroundColor Green
    Write-Host "   Size: $([math]::Round($jarInfo.Length / 1MB, 2)) MB" -ForegroundColor Gray
    Write-Host "   Built at: $($jarInfo.LastWriteTime)" -ForegroundColor Gray
} else {
    Write-Host "   JAR not found" -ForegroundColor Red
    Write-Host "   Building package..." -ForegroundColor Yellow
    mvn clean package -DskipTests
    if ($LASTEXITCODE -ne 0) {
        Write-Host "   Build failed" -ForegroundColor Red
        exit 1
    }
}

Write-Host ""
Write-Host "3. Check port 8090..." -ForegroundColor Yellow
$portInUse = Get-NetTCPConnection -LocalPort 8090 -ErrorAction SilentlyContinue
if ($portInUse) {
    $processIdToCheck = [int]($portInUse | Select-Object -First 1 -ExpandProperty OwningProcess)
    Write-Host "   Port 8090 is in use" -ForegroundColor Red
    Write-Host "   PID: $processIdToCheck" -ForegroundColor Yellow

    if ($Restart) {
        & "$PSScriptRoot\stop-mes-on-8090.ps1" -FailIfNonMes
        if ($LASTEXITCODE -ne 0) {
            exit $LASTEXITCODE
        }
        Write-Host "   Old MES process stopped" -ForegroundColor Green
    } else {
        $processInfo = Get-CimInstance Win32_Process -Filter "ProcessId = $processIdToCheck"
        $commandLine = [string]$processInfo.CommandLine
        if ($commandLine -like '*com.fine.MesApplication*' -or $commandLine -like '*MES-0.0.1-SNAPSHOT.jar*') {
            Write-Host "   MES is already running" -ForegroundColor Green
            Write-Host "   To restart: .\start-backend.ps1 -Restart" -ForegroundColor Gray
            exit 0
        }
        Write-Host "   Port 8090 is occupied by a non-MES process" -ForegroundColor Red
        Write-Host "   Release port 8090 manually or change server.port" -ForegroundColor Yellow
        exit 1
    }
} else {
    Write-Host "   Port 8090 is available" -ForegroundColor Green
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "   Starting MES..." -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Press Ctrl+C to stop" -ForegroundColor Gray
Write-Host ""

java -jar $jarPath

Write-Host ""
Write-Host "MES stopped" -ForegroundColor Yellow

# MES后端应用快速启动脚本
# 使用方法: 在PowerShell中运行 .\start-backend.ps1

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "   MES后端应用启动脚本" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 检查Redis是否运行
Write-Host "1. 检查Redis服务..." -ForegroundColor Yellow
try {
    $redisTest = & "D:\360安全浏览器下载\Redis-8.4.0-Windows-x64-cygwin\redis-cli.exe" -h 127.0.0.1 -p 6379 ping 2>$null
    if ($redisTest -eq "PONG") {
        Write-Host "   ✅ Redis运行正常" -ForegroundColor Green
    } else {
        Write-Host "   ⚠️  Redis未响应" -ForegroundColor Red
        Write-Host "   提示: 请先启动Redis服务器" -ForegroundColor Yellow
        exit 1
    }
} catch {
    Write-Host "   ❌ 无法连接Redis" -ForegroundColor Red
    Write-Host "   提示: 请确保Redis已启动" -ForegroundColor Yellow
    exit 1
}

Write-Host ""

# 检查JAR文件是否存在
Write-Host "2. 检查应用程序..." -ForegroundColor Yellow
$jarPath = "target\MES-0.0.1-SNAPSHOT.jar"
if (Test-Path $jarPath) {
    $jarInfo = Get-Item $jarPath
    Write-Host "   ✅ 找到应用文件: $($jarInfo.Name)" -ForegroundColor Green
    Write-Host "   📦 文件大小: $([math]::Round($jarInfo.Length/1MB, 2)) MB" -ForegroundColor Gray
    Write-Host "   🕐 构建时间: $($jarInfo.LastWriteTime)" -ForegroundColor Gray
} else {
    Write-Host "   ❌ 未找到JAR文件" -ForegroundColor Red
    Write-Host "   正在构建应用..." -ForegroundColor Yellow
    mvn clean package -DskipTests
    if ($LASTEXITCODE -ne 0) {
        Write-Host "   ❌ 构建失败" -ForegroundColor Red
        exit 1
    }
}

Write-Host ""

# 检查端口是否被占用
Write-Host "3. 检查端口8090..." -ForegroundColor Yellow
$portInUse = Get-NetTCPConnection -LocalPort 8090 -ErrorAction SilentlyContinue
if ($portInUse) {
    Write-Host "   ⚠️  端口8090已被占用" -ForegroundColor Red
    Write-Host "   进程ID: $($portInUse.OwningProcess)" -ForegroundColor Yellow
    $choice = Read-Host "   是否结束占用端口的进程? (y/n)"
    if ($choice -eq 'y') {
        Stop-Process -Id $portInUse.OwningProcess -Force
        Write-Host "   ✅ 已结束进程" -ForegroundColor Green
        Start-Sleep -Seconds 2
    } else {
        Write-Host "   提示: 请手动结束占用端口的进程或修改application.properties中的端口配置" -ForegroundColor Yellow
        exit 1
    }
} else {
    Write-Host "   ✅ 端口8090可用" -ForegroundColor Green
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "   启动MES应用..." -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "提示: 按 Ctrl+C 停止应用" -ForegroundColor Gray
Write-Host ""

# 启动应用
java -jar $jarPath

# 如果应用意外退出
Write-Host ""
Write-Host "应用已停止" -ForegroundColor Yellow

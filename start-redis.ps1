# Redis启动脚本
# 使用方法: 在PowerShell中运行 .\start-redis.ps1

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "   启动Redis服务器" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$redisPath = "D:\360安全浏览器下载\Redis-8.4.0-Windows-x64-cygwin"
$redisServer = Join-Path $redisPath "redis-server.exe"

if (Test-Path $redisServer) {
    Write-Host "✅ 找到Redis服务器: $redisServer" -ForegroundColor Green
    Write-Host ""
    Write-Host "启动Redis..." -ForegroundColor Yellow
    Write-Host "提示: 按 Ctrl+C 停止Redis" -ForegroundColor Gray
    Write-Host ""
    
    Set-Location $redisPath
    & $redisServer
} else {
    Write-Host "❌ 未找到Redis服务器" -ForegroundColor Red
    Write-Host "请检查Redis安装路径: $redisPath" -ForegroundColor Yellow
}

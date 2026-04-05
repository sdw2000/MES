# Redis 启动脚本（使用 PATH 中的 redis-server）
# 使用方法: 在PowerShell中运行 .\start-redis.ps1

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "   启动Redis服务器" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$existing = Get-NetTCPConnection -LocalPort 6379 -ErrorAction SilentlyContinue
if ($existing) {
    Write-Host "✅ Redis 已在运行（6379）" -ForegroundColor Green
    exit 0
}

$redisCmd = Get-Command redis-server -ErrorAction SilentlyContinue
if (-not $redisCmd) {
    Write-Host "❌ 未找到 redis-server 命令" -ForegroundColor Red
    Write-Host "请先将 Redis 安装目录加入 PATH" -ForegroundColor Yellow
    exit 1
}

Write-Host "✅ 找到命令: $($redisCmd.Source)" -ForegroundColor Green
Write-Host "正在后台启动 Redis..." -ForegroundColor Yellow

Start-Process -FilePath $redisCmd.Source -WindowStyle Hidden
Start-Sleep -Seconds 2

$conn = Get-NetTCPConnection -LocalPort 6379 -ErrorAction SilentlyContinue
if ($conn) {
    Write-Host "✅ Redis 启动成功（6379）" -ForegroundColor Green
    exit 0
}

Write-Host "❌ Redis 启动失败，请手动执行 redis-server 查看日志" -ForegroundColor Red
exit 1

# 登录接口测试脚本
# 使用方法: .\test-login.ps1 -Username "admin" -Password "your_password"

param(
    [string]$Username = "admin",
    [string]$Password = "admin123"
)

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "   测试登录接口" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$url = "http://localhost:8090/user/login"
$body = @{
    username = $Username
    password = $Password
} | ConvertTo-Json

Write-Host "请求URL: $url" -ForegroundColor Yellow
Write-Host "用户名: $Username" -ForegroundColor Yellow
Write-Host ""
Write-Host "发送登录请求..." -ForegroundColor Cyan

try {
    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    
    $response = Invoke-RestMethod -Uri $url -Method Post `
        -Body $body `
        -ContentType "application/json" `
        -TimeoutSec 60 `
        -ErrorAction Stop
    
    $stopwatch.Stop()
    $elapsed = $stopwatch.ElapsedMilliseconds
    
    Write-Host ""
    Write-Host "✅ 请求成功！" -ForegroundColor Green
    Write-Host "耗时: $elapsed ms" -ForegroundColor Green
    Write-Host ""
    Write-Host "响应数据:" -ForegroundColor Cyan
    $response | ConvertTo-Json -Depth 10
    
    if ($response.code -eq 20000) {
        Write-Host ""
        Write-Host "✅ 登录成功！" -ForegroundColor Green
        Write-Host "Token: $($response.data.token)" -ForegroundColor Yellow
    } else {
        Write-Host ""
        Write-Host "⚠️  登录失败" -ForegroundColor Red
        Write-Host "错误信息: $($response.msg)" -ForegroundColor Red
    }
    
} catch {
    Write-Host ""
    Write-Host "❌ 请求失败" -ForegroundColor Red
    Write-Host "错误信息: $($_.Exception.Message)" -ForegroundColor Red
    
    if ($_.Exception.InnerException) {
        Write-Host "详细错误: $($_.Exception.InnerException.Message)" -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "提示：请查看后端控制台的详细日志" -ForegroundColor Gray

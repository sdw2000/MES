# 快速启动前后端服务脚本
Write-Host "================================" -ForegroundColor Cyan
Write-Host "  快速启动前后端服务" -ForegroundColor Yellow
Write-Host "================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "当前状态检查..." -ForegroundColor Green
Write-Host ""

# 检查后端
$backend = netstat -ano | findstr ":8090"
if ($backend) {
    Write-Host "✅ 后端服务正在运行 (端口 8090)" -ForegroundColor Green
} else {
    Write-Host "❌ 后端服务未运行" -ForegroundColor Red
}

# 检查前端
$frontend = netstat -ano | findstr ":8080"
if ($frontend) {
    Write-Host "✅ 前端服务正在运行 (端口 8080)" -ForegroundColor Green
} else {
    Write-Host "❌ 前端服务未运行" -ForegroundColor Red
}

Write-Host ""
Write-Host "================================" -ForegroundColor Cyan
Write-Host "  启动服务" -ForegroundColor Yellow
Write-Host "================================" -ForegroundColor Cyan
Write-Host ""

# 启动后端
if (-not $backend) {
    Write-Host "正在启动后端服务（新窗口）..." -ForegroundColor Cyan
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd E:\java\MES; Write-Host '=== 后端服务 ===' -ForegroundColor Green; Write-Host ''; Write-Host '正在启动...' -ForegroundColor Yellow; mvn spring-boot:run"
    Write-Host "✅ 后端启动命令已执行" -ForegroundColor Green
    Write-Host "   等待启动（约30秒）" -ForegroundColor Gray
    Start-Sleep -Seconds 3
}

# 启动前端
if (-not $frontend) {
    Write-Host ""
    Write-Host "正在启动前端服务（新窗口）..." -ForegroundColor Cyan
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd E:\vue\ERP; Write-Host '=== 前端服务 ===' -ForegroundColor Green; Write-Host ''; Write-Host '正在启动...' -ForegroundColor Yellow; npm run dev"
    Write-Host "✅ 前端启动命令已执行" -ForegroundColor Green
    Write-Host "   等待启动（约20秒）" -ForegroundColor Gray
}

Write-Host ""
Write-Host "================================" -ForegroundColor Cyan
Write-Host "  等待服务启动" -ForegroundColor Yellow
Write-Host "================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "请等待约30秒，然后：" -ForegroundColor Yellow
Write-Host ""
Write-Host "1. 检查后端窗口" -ForegroundColor White
Write-Host "   看到 'Started MesApplication' 表示后端启动成功" -ForegroundColor Gray
Write-Host ""
Write-Host "2. 检查前端窗口" -ForegroundColor White
Write-Host "   看到 'App running at' 表示前端启动成功" -ForegroundColor Gray
Write-Host ""
Write-Host "3. 访问登录页面" -ForegroundColor White
Write-Host "   http://localhost:8080" -ForegroundColor Cyan
Write-Host ""
Write-Host "4. 使用管理员账号登录" -ForegroundColor White
Write-Host "   用户名: admin" -ForegroundColor Gray
Write-Host "   密码: [您的密码]" -ForegroundColor Gray
Write-Host ""

Write-Host "提示: 如果还是无法登录，请查看后端窗口的错误信息" -ForegroundColor Yellow
Write-Host ""

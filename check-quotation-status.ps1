# 报价单快速验证脚本
Write-Host "================================" -ForegroundColor Cyan
Write-Host "  报价单系统状态检查" -ForegroundColor Yellow
Write-Host "================================" -ForegroundColor Cyan
Write-Host ""

# 检查后端
Write-Host "[1/3] 后端服务 (端口 8090)" -ForegroundColor Green
$backend = netstat -ano | findstr ":8090" | Select-Object -First 1
if ($backend) {
    Write-Host "  ✅ 后端服务正在运行" -ForegroundColor Green
    Write-Host "  $backend" -ForegroundColor Gray
} else {
    Write-Host "  ❌ 后端服务未运行" -ForegroundColor Red
    Write-Host "  启动命令: cd E:\java\MES; mvn spring-boot:run" -ForegroundColor Yellow
}
Write-Host ""

# 检查前端
Write-Host "[2/3] 前端服务 (端口 9527)" -ForegroundColor Green
$frontend = netstat -ano | findstr ":9527" | Select-Object -First 1
if ($frontend) {
    Write-Host "  ✅ 前端服务正在运行" -ForegroundColor Green
    Write-Host "  $frontend" -ForegroundColor Gray
} else {
    Write-Host "  ❌ 前端服务未运行" -ForegroundColor Red
    Write-Host "  启动命令: cd E:\vue\ERP; npm run dev" -ForegroundColor Yellow
}
Write-Host ""

# 检查路由配置
Write-Host "[3/3] 路由配置" -ForegroundColor Green
$routerFile = "E:\vue\ERP\src\router\index.js"
$routerContent = Get-Content $routerFile -Raw
if ($routerContent -match "quotations") {
    Write-Host "  ✅ 路由已更新为 quotations" -ForegroundColor Green
} else {
    Write-Host "  ⚠️  路由可能未更新" -ForegroundColor Yellow
}
Write-Host ""

Write-Host "================================" -ForegroundColor Cyan
Write-Host "  操作建议" -ForegroundColor Yellow
Write-Host "================================" -ForegroundColor Cyan
Write-Host ""

if (-not $frontend) {
    Write-Host "⚠️  需要启动前端服务！" -ForegroundColor Yellow
    Write-Host ""
    $startFrontend = Read-Host "是否现在启动前端服务？(y/n)"
    if ($startFrontend -eq 'y' -or $startFrontend -eq 'Y') {
        Write-Host "正在启动前端服务（新窗口）..." -ForegroundColor Cyan
        Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd E:\vue\ERP; Write-Host '启动前端...' -ForegroundColor Green; npm run dev"
        Write-Host "✅ 前端启动命令已执行" -ForegroundColor Green
        Write-Host ""
        Write-Host "请等待约20秒，然后访问: http://localhost:9527" -ForegroundColor Cyan
    }
} else {
    Write-Host "✅ 服务已就绪！" -ForegroundColor Green
    Write-Host ""
    Write-Host "访问步骤：" -ForegroundColor Cyan
    Write-Host "  1. 浏览器打开: http://localhost:9527" -ForegroundColor White
    Write-Host "  2. 使用 admin 账号登录" -ForegroundColor White
    Write-Host "  3. 导航到: 销售 → 报价单管理" -ForegroundColor White
    Write-Host "  4. 新的URL: http://localhost:9527/#/sales/quotations" -ForegroundColor White
}
Write-Host ""

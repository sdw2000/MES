# 验证"首页"修改脚本
Write-Host "================================" -ForegroundColor Cyan
Write-Host "  验证导航栏'首页'修改" -ForegroundColor Yellow
Write-Host "================================" -ForegroundColor Cyan
Write-Host ""

# 检查文件是否已修改
$routerFile = "E:\vue\ERP\src\router\index.js"
$content = Get-Content $routerFile -Raw

Write-Host "[1/2] 检查路由配置..." -ForegroundColor Green
if ($content -match "title:\s*['\`"]首页['\`"]") {
    Write-Host "  ✅ 路由配置已更新为'首页'" -ForegroundColor Green
} else {
    Write-Host "  ❌ 未找到'首页'配置" -ForegroundColor Red
    if ($content -match "title:\s*['\`"]Dashboard['\`"]") {
        Write-Host "  ⚠️  仍然是'Dashboard'" -ForegroundColor Yellow
    } elseif ($content -match "title:\s*['\`"]方恩电子ERP['\`"]") {
        Write-Host "  ⚠️  仍然是'方恩电子ERP'" -ForegroundColor Yellow
    }
}

Write-Host ""
Write-Host "[2/2] 检查前端服务..." -ForegroundColor Green
$frontend = netstat -ano | findstr ":9527" | Select-Object -First 1
if ($frontend) {
    Write-Host "  ✅ 前端服务正在运行" -ForegroundColor Green
    Write-Host "  $frontend" -ForegroundColor Gray
    Write-Host ""
    Write-Host "  ⚠️  需要重启前端服务才能看到修改效果" -ForegroundColor Yellow
} else {
    Write-Host "  ⚠️  前端服务未运行" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "================================" -ForegroundColor Cyan
Write-Host "  下一步操作" -ForegroundColor Yellow
Write-Host "================================" -ForegroundColor Cyan
Write-Host ""

if ($frontend) {
    Write-Host "前端服务正在运行，需要重启：" -ForegroundColor Yellow
    Write-Host ""
    $restart = Read-Host "是否现在重启前端服务？(y/n)"
    if ($restart -eq 'y' -or $restart -eq 'Y') {
        Write-Host ""
        Write-Host "请按以下步骤操作：" -ForegroundColor Cyan
        Write-Host "  1. 在前端服务窗口按 Ctrl+C 停止服务" -ForegroundColor White
        Write-Host "  2. 按任意键启动新服务..." -ForegroundColor White
        $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
        
        Write-Host ""
        Write-Host "正在启动前端服务（新窗口）..." -ForegroundColor Green
        Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd E:\vue\ERP; Write-Host '启动前端服务...' -ForegroundColor Green; npm run dev"
        
        Write-Host "✅ 前端启动命令已执行" -ForegroundColor Green
        Write-Host ""
        Write-Host "等待服务启动完成（约20秒）..." -ForegroundColor Yellow
        Write-Host "然后刷新浏览器查看效果（Ctrl+Shift+R）" -ForegroundColor Cyan
    }
} else {
    Write-Host "前端服务未运行，启动前端服务：" -ForegroundColor Yellow
    Write-Host ""
    $start = Read-Host "是否现在启动前端服务？(y/n)"
    if ($start -eq 'y' -or $start -eq 'Y') {
        Write-Host ""
        Write-Host "正在启动前端服务（新窗口）..." -ForegroundColor Green
        Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd E:\vue\ERP; Write-Host '启动前端服务...' -ForegroundColor Green; npm run dev"
        
        Write-Host "✅ 前端启动命令已执行" -ForegroundColor Green
        Write-Host ""
        Write-Host "等待服务启动完成（约20秒）..." -ForegroundColor Yellow
        Write-Host "然后访问: http://localhost:9527" -ForegroundColor Cyan
    }
}

Write-Host ""
Write-Host "================================" -ForegroundColor Cyan
Write-Host "  预期效果" -ForegroundColor Yellow
Write-Host "================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "访问系统后，您会看到：" -ForegroundColor Green
Write-Host "  ✓ 顶部导航栏显示'首页'" -ForegroundColor White
Write-Host "  ✓ 面包屑导航显示'首页'" -ForegroundColor White
Write-Host "  ✓ 标签页显示'首页'" -ForegroundColor White
Write-Host "  ✓ 该标签页不能关闭（固定标签）" -ForegroundColor White
Write-Host ""

Write-Host "完成！" -ForegroundColor Green

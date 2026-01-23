# 报价单系统快速启动脚本
# 用途：一键启动报价单系统测试

Write-Host "================================" -ForegroundColor Cyan
Write-Host "  报价单系统快速启动向导" -ForegroundColor Yellow
Write-Host "================================" -ForegroundColor Cyan
Write-Host ""

# 检查编译状态
Write-Host "[1/4] 检查编译状态..." -ForegroundColor Green
if (Test-Path "E:\java\MES\target\classes\com\fine\serviceIMPL\QuotationServiceImpl.class") {
    Write-Host "  ✅ 编译成功，class文件已生成" -ForegroundColor Green
} else {
    Write-Host "  ❌ 未找到编译文件，开始编译..." -ForegroundColor Yellow
    cd E:\java\MES
    mvn clean compile
    if ($LASTEXITCODE -ne 0) {
        Write-Host "  ❌ 编译失败，请检查错误信息" -ForegroundColor Red
        exit 1
    }
}
Write-Host ""

# 询问是否初始化数据库
Write-Host "[2/4] 数据库初始化" -ForegroundColor Green
$initDB = Read-Host "是否需要初始化报价单数据库？(y/n)"
if ($initDB -eq 'y' -or $initDB -eq 'Y') {
    Write-Host "  正在执行数据库初始化..." -ForegroundColor Cyan
    & "E:\java\MES\setup-quotation-database.ps1"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "  ⚠️  数据库初始化可能失败，请检查MySQL连接" -ForegroundColor Yellow
    } else {
        Write-Host "  ✅ 数据库初始化完成" -ForegroundColor Green
    }
} else {
    Write-Host "  ⏭️  跳过数据库初始化" -ForegroundColor Yellow
}
Write-Host ""

# 询问是否启动后端
Write-Host "[3/4] 后端服务启动" -ForegroundColor Green
$startBackend = Read-Host "是否启动后端服务？(y/n)"
if ($startBackend -eq 'y' -or $startBackend -eq 'Y') {
    Write-Host "  正在启动Spring Boot后端服务..." -ForegroundColor Cyan
    Write-Host "  提示：后端将在新窗口中启动" -ForegroundColor Yellow
    Write-Host "  访问地址：http://localhost:8093" -ForegroundColor Cyan
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd E:\java\MES; Write-Host '启动后端服务...' -ForegroundColor Green; mvn spring-boot:run"
    Write-Host "  ✅ 后端服务启动命令已执行（新窗口）" -ForegroundColor Green
    Start-Sleep -Seconds 3
} else {
    Write-Host "  ⏭️  跳过后端启动" -ForegroundColor Yellow
}
Write-Host ""

# 询问是否启动前端
Write-Host "[4/4] 前端服务启动" -ForegroundColor Green
$startFrontend = Read-Host "是否启动前端服务？(y/n)"
if ($startFrontend -eq 'y' -or $startFrontend -eq 'Y') {
    Write-Host "  正在检查前端依赖..." -ForegroundColor Cyan
    if (-not (Test-Path "E:\vue\ERP\node_modules")) {
        Write-Host "  ⚠️  未找到node_modules，请先运行 npm install" -ForegroundColor Yellow
        $install = Read-Host "是否现在安装依赖？(y/n)"
        if ($install -eq 'y' -or $install -eq 'Y') {
            cd E:\vue\ERP
            npm install
        }
    }
    Write-Host "  正在启动Vue前端服务..." -ForegroundColor Cyan
    Write-Host "  提示：前端将在新窗口中启动" -ForegroundColor Yellow
    Write-Host "  访问地址：http://localhost:9527" -ForegroundColor Cyan
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd E:\vue\ERP; Write-Host '启动前端服务...' -ForegroundColor Green; npm run dev"
    Write-Host "  ✅ 前端服务启动命令已执行（新窗口）" -ForegroundColor Green
} else {
    Write-Host "  ⏭️  跳过前端启动" -ForegroundColor Yellow
}
Write-Host ""

# 完成提示
Write-Host "================================" -ForegroundColor Cyan
Write-Host "  启动流程完成！" -ForegroundColor Yellow
Write-Host "================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "📋 下一步操作：" -ForegroundColor Green
Write-Host "  1. 等待后端服务启动完成（约30秒）" -ForegroundColor White
Write-Host "  2. 等待前端服务启动完成（约20秒）" -ForegroundColor White
Write-Host "  3. 浏览器访问：http://localhost:9527" -ForegroundColor White
Write-Host "  4. 登录系统后访问：销售管理 → 报价单管理" -ForegroundColor White
Write-Host ""
Write-Host "🔍 测试功能：" -ForegroundColor Green
Write-Host "  ✓ 报价单列表查询" -ForegroundColor White
Write-Host "  ✓ 新增报价单" -ForegroundColor White
Write-Host "  ✓ 编辑报价单" -ForegroundColor White
Write-Host "  ✓ 查看报价单详情" -ForegroundColor White
Write-Host "  ✓ 删除报价单（逻辑删除）" -ForegroundColor White
Write-Host ""
Write-Host "📚 参考文档：" -ForegroundColor Green
Write-Host "  - QUOTATION-COMPILE-SUCCESS.md（编译成功报告）" -ForegroundColor White
Write-Host "  - QUOTATION-QUICKSTART.md（快速启动指南）" -ForegroundColor White
Write-Host "  - QUOTATION-README.md（完整文档）" -ForegroundColor White
Write-Host ""
Write-Host "✅ 所有准备工作已完成，祝测试顺利！" -ForegroundColor Green
Write-Host ""

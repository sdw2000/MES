# 报价单简化功能快速部署脚本
# 用途：一键完成所有部署步骤

Write-Host "================================" -ForegroundColor Cyan
Write-Host "  报价单简化功能部署向导" -ForegroundColor Yellow
Write-Host "================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "📋 本次修改内容：" -ForegroundColor Green
Write-Host "  ✓ 新增明细行按钮移到右边" -ForegroundColor White
Write-Host "  ✓ 删除数量、平米数、金额字段" -ForegroundColor White
Write-Host "  ✓ 新增备注字段" -ForegroundColor White
Write-Host "  ✓ 简化录入流程" -ForegroundColor White
Write-Host ""

Write-Host "⚠️  部署步骤：" -ForegroundColor Yellow
Write-Host "  1. 修改数据库表结构" -ForegroundColor White
Write-Host "  2. 编译后端代码" -ForegroundColor White
Write-Host "  3. 重启后端服务" -ForegroundColor White
Write-Host "  4. 重启前端服务" -ForegroundColor White
Write-Host ""

$confirm = Read-Host "是否开始部署？(y/n)"
if ($confirm -ne 'y' -and $confirm -ne 'Y') {
    Write-Host "部署已取消" -ForegroundColor Yellow
    exit 0
}

Write-Host ""
Write-Host "================================" -ForegroundColor Cyan
Write-Host "  步骤 1/4: 修改数据库表结构" -ForegroundColor Yellow
Write-Host "================================" -ForegroundColor Cyan

$updateDB = Read-Host "是否执行数据库修改？(y/n)"
if ($updateDB -eq 'y' -or $updateDB -eq 'Y') {
    & "E:\java\MES\update-quotation-table-structure.ps1"
    Write-Host ""
    $dbResult = Read-Host "数据库修改是否成功？(y/n)"
    if ($dbResult -ne 'y' -and $dbResult -ne 'Y') {
        Write-Host "❌ 数据库修改失败，部署中止" -ForegroundColor Red
        exit 1
    }
} else {
    Write-Host "⏭️  跳过数据库修改" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "================================" -ForegroundColor Cyan
Write-Host "  步骤 2/4: 编译后端代码" -ForegroundColor Yellow
Write-Host "================================" -ForegroundColor Cyan

cd E:\java\MES
Write-Host "正在编译..." -ForegroundColor Cyan
$compileResult = mvn clean compile -DskipTests 2>&1

if ($compileResult -match "BUILD SUCCESS") {
    Write-Host "✅ 编译成功" -ForegroundColor Green
} else {
    Write-Host "❌ 编译失败" -ForegroundColor Red
    Write-Host "错误信息：" -ForegroundColor Yellow
    $compileResult | Select-String -Pattern "\[ERROR\]" | ForEach-Object { Write-Host $_.Line -ForegroundColor Red }
    Write-Host ""
    $continueAnyway = Read-Host "是否继续？(y/n)"
    if ($continueAnyway -ne 'y' -and $continueAnyway -ne 'Y') {
        exit 1
    }
}

Write-Host ""
Write-Host "================================" -ForegroundColor Cyan
Write-Host "  步骤 3/4: 重启后端服务" -ForegroundColor Yellow
Write-Host "================================" -ForegroundColor Cyan

Write-Host "⚠️  请先停止当前后端服务（如果正在运行）" -ForegroundColor Yellow
Write-Host "    在后端窗口按 Ctrl+C" -ForegroundColor Gray
Write-Host ""

$restartBackend = Read-Host "是否启动后端服务？(y/n)"
if ($restartBackend -eq 'y' -or $restartBackend -eq 'Y') {
    Write-Host "正在启动后端服务（新窗口）..." -ForegroundColor Cyan
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd E:\java\MES; Write-Host '启动后端服务...' -ForegroundColor Green; mvn spring-boot:run"
    Write-Host "✅ 后端启动命令已执行" -ForegroundColor Green
    Write-Host "    请等待服务启动完成（约30秒）" -ForegroundColor Gray
    Start-Sleep -Seconds 5
} else {
    Write-Host "⏭️  跳过后端启动" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "================================" -ForegroundColor Cyan
Write-Host "  步骤 4/4: 重启前端服务" -ForegroundColor Yellow
Write-Host "================================" -ForegroundColor Cyan

Write-Host "⚠️  请先停止当前前端服务（如果正在运行）" -ForegroundColor Yellow
Write-Host "    在前端窗口按 Ctrl+C" -ForegroundColor Gray
Write-Host ""

$restartFrontend = Read-Host "是否启动前端服务？(y/n)"
if ($restartFrontend -eq 'y' -or $restartFrontend -eq 'Y') {
    Write-Host "正在启动前端服务（新窗口）..." -ForegroundColor Cyan
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd E:\vue\ERP; Write-Host '启动前端服务...' -ForegroundColor Green; npm run dev"
    Write-Host "✅ 前端启动命令已执行" -ForegroundColor Green
    Write-Host "    请等待服务启动完成（约20秒）" -ForegroundColor Gray
} else {
    Write-Host "⏭️  跳过前端启动" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "================================" -ForegroundColor Cyan
Write-Host "  部署完成！" -ForegroundColor Green
Write-Host "================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "📝 下一步操作：" -ForegroundColor Yellow
Write-Host ""
Write-Host "1. 等待服务启动完成" -ForegroundColor White
Write-Host "   后端: 约30秒，看到 'Started MesApplication' 即可" -ForegroundColor Gray
Write-Host "   前端: 约20秒，看到 'App running at' 即可" -ForegroundColor Gray
Write-Host ""
Write-Host "2. 访问报价单管理页面" -ForegroundColor White
Write-Host "   URL: http://localhost:9527/#/sales/quotations" -ForegroundColor Cyan
Write-Host ""
Write-Host "3. 测试新功能" -ForegroundColor White
Write-Host "   ✓ 点击右上角'新增明细行'按钮" -ForegroundColor Gray
Write-Host "   ✓ 检查表格列：物料、规格、尺寸、单位、单价、备注" -ForegroundColor Gray
Write-Host "   ✓ 确认没有：数量、平米数、金额列" -ForegroundColor Gray
Write-Host "   ✓ 填写数据并保存" -ForegroundColor Gray
Write-Host ""
Write-Host "4. 清除浏览器缓存" -ForegroundColor White
Write-Host "   按 Ctrl+Shift+R 强制刷新" -ForegroundColor Cyan
Write-Host ""

Write-Host "📚 参考文档：" -ForegroundColor Yellow
Write-Host "  QUOTATION-SIMPLIFICATION-REPORT.md - 完整修改报告" -ForegroundColor White
Write-Host ""

Write-Host "✅ 部署流程已完成！" -ForegroundColor Green
Write-Host ""

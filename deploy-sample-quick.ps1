# ========================================
#  送样功能 - 一键部署脚本（简化版）
#  执行所有必要的部署步骤
# ========================================

Write-Host "================================================" -ForegroundColor Cyan
Write-Host "  送样功能一键部署" -ForegroundColor Cyan
Write-Host "  Sample Management Quick Deploy" -ForegroundColor Cyan
Write-Host "================================================" -ForegroundColor Cyan
Write-Host ""

# 步骤1: 替换前端文件
Write-Host "步骤 1/3: 更新前端文件" -ForegroundColor Yellow
Write-Host "---------------------------------------" -ForegroundColor Gray

$sourceFile = "E:\vue\ERP\src\views\sales\samples_new.vue"
$targetFile = "E:\vue\ERP\src\views\sales\samples.vue"

if (Test-Path $sourceFile) {
    # 备份原文件
    if (Test-Path $targetFile) {
        $backupFile = "$targetFile.backup." + (Get-Date -Format "yyyyMMddHHmmss")
        Copy-Item $targetFile $backupFile -ErrorAction SilentlyContinue
        Write-Host "  ✓ 已备份: samples.vue.backup.*" -ForegroundColor Green
    }
    
    # 替换文件
    Copy-Item $sourceFile $targetFile -Force
    Write-Host "  ✓ 前端文件已更新" -ForegroundColor Green
} else {
    Write-Host "  ✗ 源文件不存在: $sourceFile" -ForegroundColor Red
    exit 1
}

Write-Host ""

# 步骤2: 提示执行数据库脚本
Write-Host "步骤 2/3: 数据库表创建" -ForegroundColor Yellow
Write-Host "---------------------------------------" -ForegroundColor Gray
Write-Host ""
Write-Host "请在MySQL中执行以下命令:" -ForegroundColor White
Write-Host ""
Write-Host "USE erp_system;" -ForegroundColor Cyan
Write-Host "SOURCE E:/java/MES/create-sample-tables.sql;" -ForegroundColor Cyan
Write-Host ""
Write-Host "或使用命令:" -ForegroundColor White
Write-Host "mysql -u root -p erp_system < E:\java\MES\create-sample-tables.sql" -ForegroundColor Cyan
Write-Host ""

$dbConfirm = Read-Host "数据库表是否已创建? (Y/N)"
if ($dbConfirm -ne "Y" -and $dbConfirm -ne "y") {
    Write-Host ""
    Write-Host "请先创建数据库表，然后重新运行此脚本" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "提示: 打开MySQL客户端，执行上述命令" -ForegroundColor Gray
    Write-Host ""
    exit 0
}

Write-Host "  ✓ 数据库表已确认" -ForegroundColor Green
Write-Host ""

# 步骤3: 重启服务
Write-Host "步骤 3/3: 重启服务" -ForegroundColor Yellow
Write-Host "---------------------------------------" -ForegroundColor Gray

$restartConfirm = Read-Host "是否重启前后端服务? (Y/N)"
if ($restartConfirm -eq "Y" -or $restartConfirm -eq "y") {
    Write-Host ""
    Write-Host "正在停止现有服务..." -ForegroundColor White
    
    # 停止Java进程
    Get-Process -Name "java" -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
    Write-Host "  ✓ 已停止后端服务" -ForegroundColor Green
    
    # 停止Node进程
    Get-Process -Name "node" -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
    Write-Host "  ✓ 已停止前端服务" -ForegroundColor Green
    
    Start-Sleep -Seconds 2
    
    Write-Host ""
    Write-Host "正在启动服务..." -ForegroundColor White
    
    # 启动后端
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd E:\java\MES; Write-Host '启动后端服务...' -ForegroundColor Cyan; mvn spring-boot:run"
    Write-Host "  ✓ 后端服务启动中..." -ForegroundColor Green
    
    Start-Sleep -Seconds 5
    
    # 启动前端
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd E:\vue\ERP; Write-Host '启动前端服务...' -ForegroundColor Cyan; npm run dev"
    Write-Host "  ✓ 前端服务启动中..." -ForegroundColor Green
    
    Write-Host ""
    Write-Host "服务启动中，请等待30-60秒..." -ForegroundColor Yellow
} else {
    Write-Host "  - 已跳过服务重启" -ForegroundColor Gray
    Write-Host ""
    Write-Host "提示: 如需重启，请手动执行:" -ForegroundColor Yellow
    Write-Host "  cd E:\java\MES; mvn spring-boot:run" -ForegroundColor Gray
    Write-Host "  cd E:\vue\ERP; npm run dev" -ForegroundColor Gray
}

Write-Host ""
Write-Host "================================================" -ForegroundColor Cyan
Write-Host "  部署完成" -ForegroundColor Cyan
Write-Host "================================================" -ForegroundColor Cyan
Write-Host ""

# 显示访问信息
Write-Host "📝 访问信息:" -ForegroundColor Yellow
Write-Host ""
Write-Host "前端地址: " -NoNewline
Write-Host "http://localhost:8080" -ForegroundColor Cyan
Write-Host "后端地址: " -NoNewline
Write-Host "http://localhost:8090" -ForegroundColor Cyan
Write-Host ""
Write-Host "登录账号: " -NoNewline
Write-Host "admin / 123456" -ForegroundColor White
Write-Host "功能入口: " -NoNewline
Write-Host "销售管理 → 送样管理" -ForegroundColor White
Write-Host ""

# 显示功能特性
Write-Host "✨ 功能特性:" -ForegroundColor Yellow
Write-Host ""
Write-Host "1. ✓ 关联客户（自动填充联系人信息）" -ForegroundColor Green
Write-Host "2. ✓ 批次号在明细表格中" -ForegroundColor Green
Write-Host "3. ✓ 备注在明细行显示" -ForegroundColor Green
Write-Host "4. ✓ 快递公司可选可输" -ForegroundColor Green
Write-Host "5. ✓ 快递单号自动查询物流" -ForegroundColor Green
Write-Host "6. ✓ 状态自动更新" -ForegroundColor Green
Write-Host ""

# 显示测试步骤
Write-Host "📋 测试步骤:" -ForegroundColor Yellow
Write-Host ""
Write-Host "1. 访问 http://localhost:8080" -ForegroundColor White
Write-Host "2. 登录系统" -ForegroundColor White
Write-Host "3. 点击 销售管理 → 送样管理" -ForegroundColor White
Write-Host "4. 点击 新建送样" -ForegroundColor White
Write-Host "5. 填写信息并保存" -ForegroundColor White
Write-Host "6. 点击 物流 维护快递信息" -ForegroundColor White
Write-Host ""

# 显示文档
Write-Host "📚 相关文档:" -ForegroundColor Yellow
Write-Host ""
Write-Host "详细设计: SAMPLE-FEATURE-DESIGN.md" -ForegroundColor Gray
Write-Host "实现报告: SAMPLE-FEATURE-IMPLEMENTATION.md" -ForegroundColor Gray
Write-Host "快速开始: SAMPLE-START-NOW.md" -ForegroundColor Gray
Write-Host "错误修复: SAMPLE-ERROR-FIX-COMPLETE.md" -ForegroundColor Gray
Write-Host ""

Write-Host "================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "🎉 部署完成！祝您使用愉快！" -ForegroundColor Green
Write-Host ""

# 询问是否打开浏览器
$openBrowser = Read-Host "是否打开浏览器访问系统? (Y/N)"
if ($openBrowser -eq "Y" -or $openBrowser -eq "y") {
    Start-Sleep -Seconds 3
    Start-Process "http://localhost:8080"
    Write-Host "✓ 浏览器已打开" -ForegroundColor Green
}

Write-Host ""

# ========================================
#  送样功能部署脚本
#  一键部署数据库表和重启服务
# ========================================

Write-Host "================================================" -ForegroundColor Cyan
Write-Host "  送样功能部署工具" -ForegroundColor Cyan
Write-Host "  Sample Management Deployment" -ForegroundColor Cyan
Write-Host "================================================" -ForegroundColor Cyan
Write-Host ""

# 检查MySQL是否运行
Write-Host "检查MySQL服务..." -ForegroundColor Yellow
$mysqlService = Get-Service -Name "MySQL*" -ErrorAction SilentlyContinue
if ($mysqlService) {
    Write-Host "  ✓ MySQL服务运行中" -ForegroundColor Green
} else {
    Write-Host "  ✗ MySQL服务未运行，请先启动MySQL" -ForegroundColor Red
    exit 1
}

Write-Host ""

# 步骤1: 执行数据库脚本
Write-Host "步骤 1: 创建数据库表" -ForegroundColor Yellow
Write-Host "---------------------------------------" -ForegroundColor Gray

$confirm = Read-Host "是否执行数据库脚本? (Y/N)"
if ($confirm -eq "Y" -or $confirm -eq "y") {
    Write-Host "正在执行SQL脚本..." -ForegroundColor White
    
    # MySQL连接信息（根据实际情况修改）
    $mysqlHost = "localhost"
    $mysqlUser = "root"
    $mysqlPort = "3306"
    $sqlFile = "E:\java\MES\create-sample-tables.sql"
    
    Write-Host "请输入MySQL密码: " -NoNewline -ForegroundColor Cyan
    $mysqlPassword = Read-Host -AsSecureString
    $BSTR = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($mysqlPassword)
    $password = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($BSTR)
    
    try {
        & mysql -h $mysqlHost -u $mysqlUser -p$password -P $mysqlPort < $sqlFile
        Write-Host "  ✓ 数据库表创建成功" -ForegroundColor Green
    } catch {
        Write-Host "  ✗ 数据库脚本执行失败: $_" -ForegroundColor Red
        Write-Host ""
        Write-Host "请手动执行SQL脚本: $sqlFile" -ForegroundColor Yellow
    }
} else {
    Write-Host "  - 已跳过数据库脚本执行" -ForegroundColor Gray
}

Write-Host ""

# 步骤2: 替换前端文件
Write-Host "步骤 2: 更新前端文件" -ForegroundColor Yellow
Write-Host "---------------------------------------" -ForegroundColor Gray

$replaceVue = Read-Host "是否替换前端samples.vue文件? (Y/N)"
if ($replaceVue -eq "Y" -or $replaceVue -eq "y") {
    $sourceFile = "E:\vue\ERP\src\views\sales\samples_new.vue"
    $targetFile = "E:\vue\ERP\src\views\sales\samples.vue"
    
    if (Test-Path $sourceFile) {
        # 备份原文件
        if (Test-Path $targetFile) {
            $backupFile = "$targetFile.backup." + (Get-Date -Format "yyyyMMddHHmmss")
            Copy-Item $targetFile $backupFile
            Write-Host "  ✓ 已备份原文件: $backupFile" -ForegroundColor Green
        }
        
        # 替换文件
        Copy-Item $sourceFile $targetFile -Force
        Write-Host "  ✓ 前端文件已更新" -ForegroundColor Green
    } else {
        Write-Host "  ✗ 源文件不存在: $sourceFile" -ForegroundColor Red
    }
} else {
    Write-Host "  - 已跳过前端文件替换" -ForegroundColor Gray
}

Write-Host ""

# 步骤3: 编译后端
Write-Host "步骤 3: 编译后端代码" -ForegroundColor Yellow
Write-Host "---------------------------------------" -ForegroundColor Gray

$compile = Read-Host "是否重新编译后端? (Y/N)"
if ($compile -eq "Y" -or $compile -eq "y") {
    Write-Host "正在编译后端..." -ForegroundColor White
    
    Push-Location "E:\java\MES"
    
    try {
        & mvn clean compile
        Write-Host "  ✓ 后端编译成功" -ForegroundColor Green
    } catch {
        Write-Host "  ✗ 后端编译失败: $_" -ForegroundColor Red
    }
    
    Pop-Location
} else {
    Write-Host "  - 已跳过后端编译" -ForegroundColor Gray
}

Write-Host ""

# 步骤4: 重启服务
Write-Host "步骤 4: 重启服务" -ForegroundColor Yellow
Write-Host "---------------------------------------" -ForegroundColor Gray

$restart = Read-Host "是否重启前后端服务? (Y/N)"
if ($restart -eq "Y" -or $restart -eq "y") {
    Write-Host "正在启动服务..." -ForegroundColor White
    
    # 停止现有服务
    Write-Host "  - 停止现有Java进程..." -ForegroundColor Gray
    Get-Process -Name "java" -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
    
    Write-Host "  - 停止现有Node进程..." -ForegroundColor Gray
    Get-Process -Name "node" -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -like "*ERP*" } | Stop-Process -Force -ErrorAction SilentlyContinue
    
    Start-Sleep -Seconds 2
    
    # 启动后端
    Write-Host "  - 启动后端服务..." -ForegroundColor White
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd E:\java\MES; mvn spring-boot:run"
    
    Start-Sleep -Seconds 3
    
    # 启动前端
    Write-Host "  - 启动前端服务..." -ForegroundColor White
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd E:\vue\ERP; npm run dev"
    
    Write-Host "  ✓ 服务启动中..." -ForegroundColor Green
    Write-Host ""
    Write-Host "  等待服务完全启动（约30-60秒）..." -ForegroundColor Yellow
} else {
    Write-Host "  - 已跳过服务重启" -ForegroundColor Gray
}

Write-Host ""
Write-Host "================================================" -ForegroundColor Cyan
Write-Host "  部署完成" -ForegroundColor Cyan
Write-Host "================================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "📝 部署总结:" -ForegroundColor Yellow
Write-Host ""
Write-Host "1. 数据库表: " -NoNewline
Write-Host "sample_orders, sample_items, sample_status_history, sample_logistics_records" -ForegroundColor White
Write-Host ""
Write-Host "2. 后端文件:" -ForegroundColor White
Write-Host "   - SampleOrder.java (实体类)" -ForegroundColor Gray
Write-Host "   - SampleItem.java (明细实体)" -ForegroundColor Gray
Write-Host "   - SampleOrderDTO.java (DTO)" -ForegroundColor Gray
Write-Host "   - SampleOrderMapper.java (Mapper)" -ForegroundColor Gray
Write-Host "   - SampleItemMapper.java (Mapper)" -ForegroundColor Gray
Write-Host "   - SampleOrderService.java (Service接口)" -ForegroundColor Gray
Write-Host "   - SampleOrderServiceImpl.java (Service实现)" -ForegroundColor Gray
Write-Host "   - SampleController.java (Controller)" -ForegroundColor Gray
Write-Host ""
Write-Host "3. 前端文件:" -ForegroundColor White
Write-Host "   - src/views/sales/samples.vue (送样管理页面)" -ForegroundColor Gray
Write-Host ""
Write-Host "4. 访问地址: " -NoNewline
Write-Host "http://localhost:8080" -ForegroundColor Cyan
Write-Host ""
Write-Host "5. 功能菜单: " -NoNewline
Write-Host "销售管理 -> 送样管理" -ForegroundColor Cyan
Write-Host ""

Write-Host "================================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "📌 后续测试步骤:" -ForegroundColor Yellow
Write-Host ""
Write-Host "1. 等待服务启动完成（查看新打开的窗口）" -ForegroundColor White
Write-Host "2. 浏览器访问: http://localhost:8080" -ForegroundColor White
Write-Host "3. 登录系统 (admin/123456)" -ForegroundColor White
Write-Host "4. 进入 销售管理 -> 送样管理" -ForegroundColor White
Write-Host "5. 测试以下功能:" -ForegroundColor White
Write-Host "   - 新建送样" -ForegroundColor Gray
Write-Host "   - 添加明细（包含批次号）" -ForegroundColor Gray
Write-Host "   - 维护物流信息" -ForegroundColor Gray
Write-Host "   - 查询物流状态" -ForegroundColor Gray
Write-Host "   - 查看详情" -ForegroundColor Gray
Write-Host ""

Write-Host "💡 注意事项:" -ForegroundColor Yellow
Write-Host ""
Write-Host "1. 物流查询功能需要快递100 API Key（目前为模拟数据）" -ForegroundColor White
Write-Host "2. 客户关联功能需要先有客户数据" -ForegroundColor White
Write-Host "3. 如遇到问题，请查看详细文档: SAMPLE-FEATURE-DESIGN.md" -ForegroundColor White
Write-Host ""

Write-Host "全部完成！✓" -ForegroundColor Green
Write-Host ""

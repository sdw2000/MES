# ========================================
#  送样功能诊断和修复脚本
# ========================================

Write-Host "`n================================================" -ForegroundColor Cyan
Write-Host "  送样功能诊断和修复工具" -ForegroundColor Cyan
Write-Host "================================================" -ForegroundColor Cyan

# 步骤1: 检查前端文件
Write-Host "`n[1/5] 检查前端文件..." -ForegroundColor Yellow
$samplesFile = "E:\vue\ERP\src\views\sales\samples.vue"
$fileSize = (Get-Item $samplesFile).Length
if ($fileSize -gt 20000) {
    Write-Host "  ✓ 前端文件已更新 (大小: $fileSize 字节)" -ForegroundColor Green
} else {
    Write-Host "  ✗ 前端文件可能未更新 (大小: $fileSize 字节)" -ForegroundColor Red
}

# 步骤2: 检查后端服务
Write-Host "`n[2/5] 检查后端服务..." -ForegroundColor Yellow
$javaProcesses = Get-Process -Name "java" -ErrorAction SilentlyContinue
if ($javaProcesses) {
    Write-Host "  ✓ 后端Java进程运行中 (PID: $($javaProcesses.Id -join ', '))" -ForegroundColor Green
} else {
    Write-Host "  ✗ 后端Java进程未运行" -ForegroundColor Red
}

$port8090 = netstat -ano | findstr ":8090.*LISTENING"
if ($port8090) {
    Write-Host "  ✓ 端口8090正在监听" -ForegroundColor Green
} else {
    Write-Host "  ✗ 端口8090未监听" -ForegroundColor Red
}

# 步骤3: 测试API连接
Write-Host "`n[3/5] 测试API连接..." -ForegroundColor Yellow
try {
    $response = Invoke-WebRequest -Uri "http://localhost:8090/api/sales/samples" `
        -Method GET `
        -Headers @{"X-Token"="admin-token"} `
        -UseBasicParsing `
        -TimeoutSec 5 `
        -ErrorAction Stop
    
    Write-Host "  ✓ API响应正常 (状态码: $($response.StatusCode))" -ForegroundColor Green
    Write-Host "  响应内容: $($response.Content.Substring(0, [Math]::Min(100, $response.Content.Length)))..." -ForegroundColor Gray
} catch {
    Write-Host "  ✗ API调用失败: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "  可能原因: 数据库表未创建或后端未重新编译" -ForegroundColor Yellow
}

# 步骤4: 检查数据库连接配置
Write-Host "`n[4/5] 检查数据库配置..." -ForegroundColor Yellow
$appProps = Get-Content "E:\java\MES\src\main\resources\application.properties"
$dbUrl = $appProps | Where-Object { $_ -match "spring.datasource.url" }
if ($dbUrl) {
    Write-Host "  数据库URL: $dbUrl" -ForegroundColor Gray
}

# 步骤5: 提供修复建议
Write-Host "`n[5/5] 修复建议..." -ForegroundColor Yellow
Write-Host "  1. 执行数据库脚本创建表:" -ForegroundColor White
Write-Host "     手动连接到数据库执行: E:\java\MES\create-sample-tables.sql" -ForegroundColor Cyan
Write-Host ""
Write-Host "  2. 重新编译后端:" -ForegroundColor White
Write-Host "     cd E:\java\MES" -ForegroundColor Cyan
Write-Host "     mvn clean compile" -ForegroundColor Cyan
Write-Host ""
Write-Host "  3. 重启后端服务 (已生成停止和启动脚本)" -ForegroundColor White
Write-Host ""

# 生成停止和启动脚本
$stopScript = @"
Write-Host '停止所有Java进程...' -ForegroundColor Yellow
Get-Process -Name 'java' -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Sleep -Seconds 2
Write-Host '✓ Java进程已停止' -ForegroundColor Green
"@

$startScript = @"
Write-Host '启动后端服务...' -ForegroundColor Cyan
cd E:\java\MES
Start-Process powershell -ArgumentList '-NoExit', '-Command', 'cd E:\java\MES; Write-Host ""=== 后端服务启动中 ==="" -ForegroundColor Green; mvn spring-boot:run'
Write-Host '✓ 后端服务启动中，请等待30-60秒...' -ForegroundColor Yellow
"@

Set-Content -Path "E:\java\MES\stop-backend.ps1" -Value $stopScript -Encoding UTF8
Set-Content -Path "E:\java\MES\start-backend.ps1" -Value $startScript -Encoding UTF8

Write-Host "  已生成脚本:" -ForegroundColor Cyan
Write-Host "    - stop-backend.ps1  (停止后端)" -ForegroundColor Gray
Write-Host "    - start-backend.ps1 (启动后端)" -ForegroundColor Gray

Write-Host "`n================================================" -ForegroundColor Cyan
Write-Host "诊断完成！" -ForegroundColor Green
Write-Host "================================================`n" -ForegroundColor Cyan

# 询问是否立即修复
$fix = Read-Host "是否立即重新编译并重启后端? (Y/N)"
if ($fix -eq "Y" -or $fix -eq "y") {
    Write-Host "`n开始修复..." -ForegroundColor Yellow
    
    # 停止后端
    Write-Host "`n[1/3] 停止后端服务..." -ForegroundColor Yellow
    Get-Process -Name "java" -ErrorAction SilentlyContinue | Stop-Process -Force
    Start-Sleep -Seconds 2
    Write-Host "  ✓ 后端已停止" -ForegroundColor Green
    
    # 编译
    Write-Host "`n[2/3] 重新编译后端..." -ForegroundColor Yellow
    cd E:\java\MES
    mvn clean compile
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  ✓ 编译成功" -ForegroundColor Green
        
        # 启动
        Write-Host "`n[3/3] 启动后端服务..." -ForegroundColor Yellow
        Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd E:\java\MES; Write-Host '=== 后端服务启动中 ===' -ForegroundColor Green; mvn spring-boot:run"
        Write-Host "  ✓ 后端服务启动中..." -ForegroundColor Green
        
        Write-Host "`n请等待30-60秒，然后访问: http://localhost:8080" -ForegroundColor Cyan
        Write-Host "进入 销售管理 → 送样管理 测试功能" -ForegroundColor Cyan
    } else {
        Write-Host "  ✗ 编译失败，请检查错误信息" -ForegroundColor Red
    }
}

Write-Host "`n提示: 如果API仍然失败，请确保已执行数据库脚本创建表！" -ForegroundColor Yellow

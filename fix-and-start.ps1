# 修复并启动后端服务

Write-Host "===============================================" -ForegroundColor Cyan
Write-Host "   修复Java编译问题并启动服务" -ForegroundColor Cyan
Write-Host "===============================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "步骤 1: 清理编译..." -ForegroundColor Yellow
cd E:\java\MES
mvn clean

Write-Host ""
Write-Host "步骤 2: 编译项目..." -ForegroundColor Yellow
$compileResult = mvn compile -DskipTests 2>&1
$compileSuccess = $compileResult -match "BUILD SUCCESS"

if ($compileSuccess) {
    Write-Host "✅ 编译成功！" -ForegroundColor Green
} else {
    Write-Host "❌ 编译失败，查看错误..." -ForegroundColor Red
    $compileResult | Select-String -Pattern "\[ERROR\]" | Select-Object -First 10
    Write-Host ""
    Write-Host "建议：检查以下文件是否有语法错误：" -ForegroundColor Yellow
    Write-Host "  - QuotationMapper.java" -ForegroundColor White
    Write-Host "  - QuotationService.java" -ForegroundColor White
    Write-Host "  - QuotationServiceImpl.java" -ForegroundColor White
    exit 1
}

Write-Host ""
Write-Host "步骤 3: 启动Spring Boot服务..." -ForegroundColor Yellow
Write-Host "提示：服务将在 http://localhost:8090 启动" -ForegroundColor Cyan
Write-Host "提示：按 Ctrl+C 停止服务" -ForegroundColor Cyan
Write-Host ""

mvn spring-boot:run

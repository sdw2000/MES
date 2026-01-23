# 快速编译测试

Write-Host "正在编译项目..." -ForegroundColor Yellow

cd E:\java\MES

# 清理
mvn clean -q

# 编译
$result = mvn compile -DskipTests 2>&1

# 检查结果
if ($result -match "BUILD SUCCESS") {
    Write-Host "✅ 编译成功！可以启动服务了。" -ForegroundColor Green
    Write-Host ""
    Write-Host "启动命令：" -ForegroundColor Cyan
    Write-Host "  mvn spring-boot:run" -ForegroundColor White
} elseif ($result -match "BUILD FAILURE") {
    Write-Host "❌ 编译失败！" -ForegroundColor Red
    Write-Host ""
    Write-Host "错误信息：" -ForegroundColor Yellow
    $result | Select-String -Pattern "\[ERROR\]" | ForEach-Object { Write-Host $_.Line -ForegroundColor Red }
} else {
    Write-Host "⚠️  编译状态未知" -ForegroundColor Yellow
    $result | Select-Object -Last 10
}

# 送样功能 - 后端启动脚本
# 执行方式: 在PowerShell中运行此脚本

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  启动送样功能后端服务" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 进入项目目录
Set-Location "e:\java\MES"

Write-Host "步骤1: 清理旧的编译文件..." -ForegroundColor Yellow
$cleanResult = mvn clean 2>&1
if ($LASTEXITCODE -eq 0) {
    Write-Host "✅ 清理成功" -ForegroundColor Green
} else {
    Write-Host "❌ 清理失败" -ForegroundColor Red
    Write-Host $cleanResult
    exit 1
}

Write-Host ""
Write-Host "步骤2: 编译项目..." -ForegroundColor Yellow
$compileResult = mvn compile -DskipTests 2>&1
if ($LASTEXITCODE -eq 0) {
    Write-Host "✅ 编译成功" -ForegroundColor Green
} else {
    Write-Host "❌ 编译失败" -ForegroundColor Red
    Write-Host $compileResult
    exit 1
}

Write-Host ""
Write-Host "步骤3: 打包项目..." -ForegroundColor Yellow
$packageResult = mvn package -DskipTests 2>&1
if ($LASTEXITCODE -eq 0) {
    Write-Host "✅ 打包成功" -ForegroundColor Green
} else {
    Write-Host "❌ 打包失败" -ForegroundColor Red
    Write-Host $packageResult
    exit 1
}

Write-Host ""
Write-Host "步骤4: 检查JAR包..." -ForegroundColor Yellow
$jarFile = "target\MES-0.0.1-SNAPSHOT.jar"
if (Test-Path $jarFile) {
    $fileInfo = Get-Item $jarFile
    Write-Host "✅ JAR包已生成: $($fileInfo.Length) 字节" -ForegroundColor Green
} else {
    Write-Host "❌ JAR包未找到" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "步骤5: 启动服务..." -ForegroundColor Yellow
Write-Host "服务将运行在端口: 8090" -ForegroundColor Green
Write-Host "按 Ctrl+C 停止服务" -ForegroundColor Yellow
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 启动服务
java -jar $jarFile

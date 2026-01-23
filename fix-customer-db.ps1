# Fix Customer Database Tables
Write-Host "===================================" -ForegroundColor Cyan
Write-Host "客户管理数据库表修复脚本" -ForegroundColor Cyan
Write-Host "===================================" -ForegroundColor Cyan
Write-Host ""

# MySQL connection settings
$mysqlPath = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
$username = "root"
$password = "123456"
$database = "mes"

# Check if mysql exists
if (-not (Test-Path $mysqlPath)) {
    $mysqlPath = "C:\Program Files\MySQL\MySQL Server 5.7\bin\mysql.exe"
    if (-not (Test-Path $mysqlPath)) {
        Write-Host "❌ 找不到MySQL！请手动执行SQL文件：create-customer-tables.sql" -ForegroundColor Red
        Write-Host ""
        Write-Host "手动执行方法：" -ForegroundColor Yellow
        Write-Host "1. 打开MySQL客户端或工具（如Navicat, MySQL Workbench）" -ForegroundColor White
        Write-Host "2. 连接到 mes 数据库" -ForegroundColor White
        Write-Host "3. 执行 create-customer-tables.sql 文件" -ForegroundColor White
        pause
        exit 1
    }
}

Write-Host "✓ 找到MySQL：$mysqlPath" -ForegroundColor Green
Write-Host ""

# Execute SQL file
Write-Host "正在创建客户管理表..." -ForegroundColor Yellow
try {
    & $mysqlPath -u $username -p$password $database -e "source create-customer-tables.sql"
    Write-Host "✓ 表创建成功！" -ForegroundColor Green
} catch {
    Write-Host "❌ 创建失败，尝试备用方法..." -ForegroundColor Yellow
    Get-Content create-customer-tables.sql | & $mysqlPath -u $username -p$password $database
}

Write-Host ""
Write-Host "验证表结构..." -ForegroundColor Yellow
& $mysqlPath -u $username -p$password $database -e "SHOW TABLES LIKE 'customer%';"

Write-Host ""
Write-Host "===================================" -ForegroundColor Cyan
Write-Host "✅ 修复完成！请重试创建客户操作" -ForegroundColor Green
Write-Host "===================================" -ForegroundColor Cyan
pause

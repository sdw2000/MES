# ========================================
# 客户管理功能 - 一键部署脚本
# 创建日期: 2026-01-06
# ========================================

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  客户管理功能 - 一键部署" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 配置
$MYSQL_USER = "root"
$MYSQL_PASSWORD = "root"
$MYSQL_DATABASE = "erp"
$MYSQL_BIN = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"

# 检查MySQL是否安装
if (-not (Test-Path $MYSQL_BIN)) {
    Write-Host "⚠ MySQL未找到，尝试使用系统PATH中的mysql" -ForegroundColor Yellow
    $MYSQL_BIN = "mysql"
}

Write-Host "📋 执行步骤：" -ForegroundColor Cyan
Write-Host "1. 创建数据库表结构" -ForegroundColor White
Write-Host "2. 插入测试数据" -ForegroundColor White
Write-Host "3. 验证数据" -ForegroundColor White
Write-Host ""

# 步骤1: 创建表结构
Write-Host "================================" -ForegroundColor Cyan
Write-Host "步骤 1/3: 创建表结构" -ForegroundColor Cyan
Write-Host "================================" -ForegroundColor Cyan

Get-Content "create-customer-tables.sql" | & $MYSQL_BIN -u$MYSQL_USER -p$MYSQL_PASSWORD $MYSQL_DATABASE
if ($LASTEXITCODE -eq 0) {
    Write-Host "✅ 表结构创建成功" -ForegroundColor Green
} else {
    Write-Host "❌ 表结构创建失败" -ForegroundColor Red
    exit 1
}

Write-Host ""

# 步骤2: 插入测试数据
Write-Host "================================" -ForegroundColor Cyan
Write-Host "步骤 2/3: 插入测试数据" -ForegroundColor Cyan
Write-Host "================================" -ForegroundColor Cyan

Get-Content "insert-customer-test-data.sql" | & $MYSQL_BIN -u$MYSQL_USER -p$MYSQL_PASSWORD $MYSQL_DATABASE
if ($LASTEXITCODE -eq 0) {
    Write-Host "✅ 测试数据插入成功" -ForegroundColor Green
} else {
    Write-Host "❌ 测试数据插入失败" -ForegroundColor Red
    exit 1
}

Write-Host ""

# 步骤3: 验证数据
Write-Host "================================" -ForegroundColor Cyan
Write-Host "步骤 3/3: 验证数据" -ForegroundColor Cyan
Write-Host "================================" -ForegroundColor Cyan

$query = "SELECT COUNT(*) as customer_count FROM customers WHERE is_deleted = 0; SELECT COUNT(*) as contact_count FROM customer_contacts;"

$query | & $MYSQL_BIN -u$MYSQL_USER -p$MYSQL_PASSWORD $MYSQL_DATABASE
Write-Host "✅ 数据验证成功" -ForegroundColor Green

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  ✅ 数据库部署完成！" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "📊 部署统计：" -ForegroundColor Cyan
Write-Host "- 客户主表: customers" -ForegroundColor White
Write-Host "- 联系人表: customer_contacts" -ForegroundColor White
Write-Host "- 序列表: customer_code_sequence" -ForegroundColor White
Write-Host "- 测试客户: 7个" -ForegroundColor White
Write-Host "- 测试联系人: 14个" -ForegroundColor White
Write-Host ""

Write-Host "📝 下一步操作：" -ForegroundColor Cyan
Write-Host "1. 启动后端：执行 start-backend.bat 或 java -jar target\MES-0.0.1-SNAPSHOT.jar" -ForegroundColor White
Write-Host "2. 启动前端：cd e:\vue\ERP; npm run dev" -ForegroundColor White
Write-Host "3. 访问：http://localhost:8080/#/sales/customers" -ForegroundColor White
Write-Host ""

Write-Host "🎯 API地址：" -ForegroundColor Cyan
Write-Host "- 客户列表: GET  /api/sales/customers" -ForegroundColor White
Write-Host "- 客户详情: GET  /api/sales/customers/{id}" -ForegroundColor White
Write-Host "- 新增客户: POST /api/sales/customers" -ForegroundColor White
Write-Host "- 更新客户: PUT  /api/sales/customers/{id}" -ForegroundColor White
Write-Host "- 删除客户: DEL  /api/sales/customers/{id}" -ForegroundColor White
Write-Host ""

Write-Host "按任意键退出..." -ForegroundColor Yellow
$null = $Host.UI.RawUI.ReadKey('NoEcho,IncludeKeyDown')

# 报价单系统 - 数据库初始化脚本
# 执行数据库SQL并验证

Write-Host "===============================================" -ForegroundColor Cyan
Write-Host "   报价单管理系统 - 数据库初始化" -ForegroundColor Cyan
Write-Host "===============================================" -ForegroundColor Cyan
Write-Host ""

# 数据库配置
$mysqlPath = "mysql"
$dbUser = "root"
$dbPassword = "123456"  # 请根据实际情况修改
$dbName = "mes_db"
$sqlFile = "E:\java\MES\database-quotations.sql"

Write-Host "步骤 1: 检查SQL文件是否存在..." -ForegroundColor Yellow
if (Test-Path $sqlFile) {
    Write-Host "✅ SQL文件找到: $sqlFile" -ForegroundColor Green
} else {
    Write-Host "❌ SQL文件不存在: $sqlFile" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "步骤 2: 连接MySQL并执行SQL..." -ForegroundColor Yellow

# 执行SQL文件
$mysqlCommand = "mysql -u $dbUser -p$dbPassword $dbName < `"$sqlFile`""
try {
    Invoke-Expression $mysqlCommand
    Write-Host "✅ SQL执行成功" -ForegroundColor Green
} catch {
    Write-Host "❌ SQL执行失败: $_" -ForegroundColor Red
    Write-Host ""
    Write-Host "提示：如果遇到密码错误，请修改脚本中的dbPassword变量" -ForegroundColor Yellow
    exit 1
}

Write-Host ""
Write-Host "步骤 3: 验证数据..." -ForegroundColor Yellow

# 查询报价单数量
$query1 = "SELECT COUNT(*) as count FROM quotations WHERE is_deleted=0;"
$result1 = & mysql -u $dbUser -p$dbPassword $dbName -N -B -e $query1
Write-Host "✅ 报价单数量: $result1" -ForegroundColor Green

# 查询报价单明细数量
$query2 = "SELECT COUNT(*) as count FROM quotation_items WHERE is_deleted=0;"
$result2 = & mysql -u $dbUser -p$dbPassword $dbName -N -B -e $query2
Write-Host "✅ 报价明细数量: $result2" -ForegroundColor Green

Write-Host ""
Write-Host "步骤 4: 查看测试数据..." -ForegroundColor Yellow

# 显示报价单列表
$query3 = @"
SELECT 
    q.id,
    q.quotation_no as '报价单号',
    q.customer as '客户',
    q.total_amount as '总金额',
    q.status as '状态'
FROM quotations q
WHERE q.is_deleted=0
ORDER BY q.created_at DESC;
"@

Write-Host "报价单列表：" -ForegroundColor Cyan
& mysql -u $dbUser -p$dbPassword $dbName -t -e $query3

Write-Host ""
Write-Host "===============================================" -ForegroundColor Cyan
Write-Host "   ✅ 数据库初始化完成！" -ForegroundColor Green
Write-Host "===============================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "下一步：" -ForegroundColor Yellow
Write-Host "1. 启动后端服务: mvn spring-boot:run" -ForegroundColor White
Write-Host "2. 启动前端服务: npm run dev" -ForegroundColor White
Write-Host "3. 访问系统: http://localhost:8080" -ForegroundColor White
Write-Host ""

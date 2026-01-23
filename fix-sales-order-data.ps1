# =====================================================
# 快速修复销售订单数据脚本
# =====================================================

Write-Host "=== 修复销售订单数据 ===" -ForegroundColor Cyan
Write-Host ""

# 数据库连接信息
$mysqlHost = "ssdw8127.mysql.rds.aliyuncs.com"
$mysqlUser = "david"
$mysqlDb = "erp"

Write-Host "正在连接数据库..." -ForegroundColor Yellow
Write-Host "主机: $mysqlHost" -ForegroundColor Gray
Write-Host "数据库: $mysqlDb" -ForegroundColor Gray
Write-Host ""

# 执行修复SQL
$fixSql = @"
-- 删除可能存在的错误数据
DELETE FROM sales_order_items WHERE order_id = 1 AND is_deleted = 0;

-- 插入正确的明细数据
INSERT INTO sales_order_items 
(order_id, material_code, material_name, length, width, thickness, rolls, sqm, unit_price, amount, created_by, updated_by) 
VALUES 
(1, 'MT-001', '聚丙烯胶带', 1000.00, 50.00, 0.080, 10, 500.00, 25.00, 12500.00, 'admin', 'admin');

-- 验证数据
SELECT 
    so.order_no,
    so.customer,
    COUNT(soi.id) as item_count
FROM sales_orders so
LEFT JOIN sales_order_items soi ON so.id = soi.order_id AND soi.is_deleted = 0
WHERE so.order_no = 'SO-20250105-001'
GROUP BY so.order_no, so.customer;
"@

Write-Host "执行修复SQL..." -ForegroundColor Yellow
Write-Host ""

# 保存SQL到临时文件
$tempSqlFile = "$env:TEMP\fix-sales-order.sql"
$fixSql | Out-File -FilePath $tempSqlFile -Encoding UTF8

# 执行SQL（需要手动输入密码）
Write-Host "请输入数据库密码: " -ForegroundColor Green -NoNewline
mysql -h $mysqlHost -u $mysqlUser -p $mysqlDb < $tempSqlFile

Write-Host ""
Write-Host "=== 修复完成 ===" -ForegroundColor Green
Write-Host ""
Write-Host "现在请刷新浏览器页面，订单明细应该可以正常显示了。" -ForegroundColor Yellow
Write-Host ""

# 清理临时文件
Remove-Item $tempSqlFile -ErrorAction SilentlyContinue

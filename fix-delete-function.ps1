# 测试和修复删除功能

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  销售订单删除功能测试" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 1. 重新编译后端
Write-Host "[1/3] 重新编译后端..." -ForegroundColor Yellow
cd e:\java\MES
mvn clean compile -DskipTests -q

if ($LASTEXITCODE -eq 0) {
    Write-Host "  ✅ 编译成功" -ForegroundColor Green
} else {
    Write-Host "  ❌ 编译失败" -ForegroundColor Red
    exit 1
}
Write-Host ""

# 2. 检查后端是否运行
Write-Host "[2/3] 检查后端状态..." -ForegroundColor Yellow
$backend = netstat -ano | findstr ":8090" | findstr "LISTENING"
if ($backend) {
    Write-Host "  ⚠️  后端正在运行，需要重启" -ForegroundColor Yellow
    Write-Host "  请在 IDEA 中重启 MesApplication" -ForegroundColor Yellow
} else {
    Write-Host "  ❌ 后端未运行" -ForegroundColor Red
    Write-Host "  请在 IDEA 中启动 MesApplication" -ForegroundColor Yellow
}
Write-Host ""

# 3. 说明
Write-Host "[3/3] 测试步骤" -ForegroundColor Yellow
Write-Host ""
Write-Host "修复内容：" -ForegroundColor Cyan
Write-Host "  1. ✅ 改用 MyBatis-Plus 的标准删除方法" -ForegroundColor White
Write-Host "  2. ✅ deleteById() 会自动设置 is_deleted = 1" -ForegroundColor White
Write-Host "  3. ✅ 查询时自动过滤 is_deleted = 1 的数据" -ForegroundColor White
Write-Host ""

Write-Host "测试步骤：" -ForegroundColor Cyan
Write-Host "  1. 重启后端（在 IDEA 中重新运行 MesApplication）" -ForegroundColor White
Write-Host "  2. 刷新前端页面 (F5)" -ForegroundColor White
Write-Host "  3. 点击任意订单的 [删除] 按钮" -ForegroundColor White
Write-Host "  4. 确认删除" -ForegroundColor White
Write-Host "  5. 查看订单是否从列表中消失" -ForegroundColor White
Write-Host ""

Write-Host "查看日志：" -ForegroundColor Cyan
Write-Host "  后端控制台应该显示：" -ForegroundColor White
Write-Host "  === 开始删除订单 ===" -ForegroundColor Gray
Write-Host "  订单号: SO-XXXXXXXX-XXX" -ForegroundColor Gray
Write-Host "  订单ID: X" -ForegroundColor Gray
Write-Host "  订单明细数量: X" -ForegroundColor Gray
Write-Host "  === 订单删除成功 ===" -ForegroundColor Gray
Write-Host "  删除结果: 1" -ForegroundColor Gray
Write-Host "  ==================" -ForegroundColor Gray
Write-Host ""

Write-Host "如果删除后仍然显示：" -ForegroundColor Cyan
Write-Host "  1. 检查后端日志是否有错误" -ForegroundColor White
Write-Host "  2. 检查数据库 sales_orders 表的 is_deleted 字段" -ForegroundColor White
Write-Host "  3. 在浏览器 Console 查看 API 响应" -ForegroundColor White
Write-Host ""

Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "按任意键继续..." -ForegroundColor Yellow
$null = Read-Host

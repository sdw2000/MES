# 更新报价单明细表结构脚本
# 用途：删除quantity、sqm、amount字段

Write-Host "================================" -ForegroundColor Cyan
Write-Host "  报价单明细表结构更新" -ForegroundColor Yellow
Write-Host "================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "⚠️  警告：此操作将删除以下字段：" -ForegroundColor Yellow
Write-Host "  - quantity (数量)" -ForegroundColor Red
Write-Host "  - sqm (平米数)" -ForegroundColor Red
Write-Host "  - amount (金额)" -ForegroundColor Red
Write-Host ""

Write-Host "📋 影响范围：" -ForegroundColor Cyan
Write-Host "  数据库: erp" -ForegroundColor White
Write-Host "  表名: quotation_items" -ForegroundColor White
Write-Host ""

$confirm = Read-Host "是否继续？(输入 YES 确认)"

if ($confirm -ne "YES") {
    Write-Host ""
    Write-Host "❌ 操作已取消" -ForegroundColor Red
    exit 0
}

Write-Host ""
Write-Host "正在执行数据库修改..." -ForegroundColor Green

# 数据库连接信息（根据实际情况修改）
$mysqlHost = "ssdw8127.mysql.rds.aliyuncs.com"
$mysqlUser = "david"
$mysqlPassword = "dadazhengzheng@feng"
$mysqlDatabase = "erp"

$sqlFile = "E:\java\MES\update-quotation-items-table.sql"

try {
    # 执行SQL文件
    $command = "mysql -h $mysqlHost -u $mysqlUser -p$mysqlPassword $mysqlDatabase < `"$sqlFile`""
    Invoke-Expression $command
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host ""
        Write-Host "✅ 数据库表结构更新成功！" -ForegroundColor Green
        Write-Host ""
        Write-Host "📋 已删除字段：" -ForegroundColor Cyan
        Write-Host "  ✓ quantity" -ForegroundColor Green
        Write-Host "  ✓ sqm" -ForegroundColor Green
        Write-Host "  ✓ amount" -ForegroundColor Green
        Write-Host ""
        Write-Host "📝 下一步操作：" -ForegroundColor Yellow
        Write-Host "  1. 重新编译后端代码" -ForegroundColor White
        Write-Host "     命令: cd E:\java\MES; mvn clean compile" -ForegroundColor Gray
        Write-Host "  2. 重启后端服务" -ForegroundColor White
        Write-Host "     命令: mvn spring-boot:run" -ForegroundColor Gray
        Write-Host "  3. 重启前端服务" -ForegroundColor White
        Write-Host "     命令: cd E:\vue\ERP; npm run dev" -ForegroundColor Gray
        Write-Host "  4. 测试报价单功能" -ForegroundColor White
        Write-Host ""
    } else {
        Write-Host ""
        Write-Host "❌ 数据库更新失败！" -ForegroundColor Red
        Write-Host "请检查错误信息并手动执行SQL" -ForegroundColor Yellow
    }
} catch {
    Write-Host ""
    Write-Host "❌ 执行失败: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host ""
    Write-Host "💡 手动执行方法：" -ForegroundColor Yellow
    Write-Host "  1. 打开MySQL客户端" -ForegroundColor White
    Write-Host "  2. 连接到数据库: $mysqlDatabase" -ForegroundColor White
    Write-Host "  3. 执行SQL文件: source $sqlFile" -ForegroundColor White
}

Write-Host ""
Write-Host "完成！" -ForegroundColor Green

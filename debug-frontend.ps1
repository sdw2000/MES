# 销售订单前端调试脚本
# 用于检查前端页面的渲染状态和控制台输出

Write-Host "======================================" -ForegroundColor Cyan
Write-Host "  销售订单前端调试工具" -ForegroundColor Cyan
Write-Host "======================================" -ForegroundColor Cyan
Write-Host ""

# 检查服务器状态
Write-Host "[1/4] 检查服务器状态..." -ForegroundColor Yellow
$frontendPort = netstat -ano | findstr ":8080" | findstr "LISTENING"
$backendPort = netstat -ano | findstr ":8090" | findstr "LISTENING"

if ($frontendPort) {
    Write-Host "  ✅ 前端服务器运行中 (端口 8080)" -ForegroundColor Green
} else {
    Write-Host "  ❌ 前端服务器未运行" -ForegroundColor Red
    Write-Host "  请运行: cd e:\vue\ERP && npm run dev" -ForegroundColor Yellow
}

if ($backendPort) {
    Write-Host "  ✅ 后端服务器运行中 (端口 8090)" -ForegroundColor Green
} else {
    Write-Host "  ❌ 后端服务器未运行" -ForegroundColor Red
    Write-Host "  请在 IDEA 中启动后端项目" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "[2/4] 准备打开测试页面..." -ForegroundColor Yellow
Write-Host ""

# 显示可用的测试 URL
Write-Host "可用的测试页面:" -ForegroundColor Cyan
Write-Host "  1. API 测试页面: http://localhost:8080/test-api.html" -ForegroundColor White
Write-Host "  2. 订单管理页面: http://localhost:8080/#/sales/orders" -ForegroundColor White
Write-Host ""

Write-Host "[3/4] 打开 Chrome 浏览器..." -ForegroundColor Yellow

# 检查 Chrome 是否已安装
$chromePaths = @(
    "C:\Program Files\Google\Chrome\Application\chrome.exe",
    "C:\Program Files (x86)\Google\Chrome\Application\chrome.exe",
    "$env:LOCALAPPDATA\Google\Chrome\Application\chrome.exe"
)

$chromeExe = $null
foreach ($path in $chromePaths) {
    if (Test-Path $path) {
        $chromeExe = $path
        break
    }
}

if ($chromeExe) {
    Write-Host "  找到 Chrome: $chromeExe" -ForegroundColor Green
    
    # 打开浏览器开发者工具
    Write-Host ""
    Write-Host "[4/4] 启动浏览器（带开发者工具）..." -ForegroundColor Yellow
    
    # 先打开 API 测试页面
    Start-Process $chromeExe -ArgumentList "--auto-open-devtools-for-tabs", "http://localhost:8080/test-api.html"
    
    Start-Sleep -Seconds 2
    
    # 再打开订单管理页面（新标签页）
    Start-Process $chromeExe -ArgumentList "http://localhost:8080/#/sales/orders"
    
    Write-Host ""
    Write-Host "✅ 浏览器已打开！" -ForegroundColor Green
    Write-Host ""
    Write-Host "=====================================" -ForegroundColor Cyan
    Write-Host "  调试步骤指南" -ForegroundColor Cyan
    Write-Host "=====================================" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "【第一步】在 API 测试页面中:" -ForegroundColor Yellow
    Write-Host "  1. 点击 '测试连接到后端' 按钮" -ForegroundColor White
    Write-Host "  2. 点击 '获取订单' 按钮" -ForegroundColor White
    Write-Host "  3. 查看是否显示订单数据" -ForegroundColor White
    Write-Host ""
    Write-Host "【第二步】切换到订单管理页面:" -ForegroundColor Yellow
    Write-Host "  1. 打开浏览器控制台 (F12)" -ForegroundColor White
    Write-Host "  2. 切换到 Console 标签" -ForegroundColor White
    Write-Host "  3. 查找 '=== 订单数据调试 ===' 输出" -ForegroundColor White
    Write-Host ""
    Write-Host "【第三步】检查调试信息:" -ForegroundColor Yellow
    Write-Host "  在页面顶部应该看到灰色的调试信息框，显示:" -ForegroundColor White
    Write-Host "    - orders 数组长度" -ForegroundColor White
    Write-Host "    - pagedOrders 数组长度" -ForegroundColor White
    Write-Host "    - 当前页、每页大小、总数" -ForegroundColor White
    Write-Host ""
    Write-Host "【第四步】检查表格:" -ForegroundColor Yellow
    Write-Host "  如果调试信息显示有数据但表格为空:" -ForegroundColor White
    Write-Host "    - 检查 Network 标签，查看 API 请求" -ForegroundColor White
    Write-Host "    - 检查 Console 标签，查看是否有错误" -ForegroundColor White
    Write-Host "    - 检查 Elements 标签，查看 DOM 结构" -ForegroundColor White
    Write-Host ""
    Write-Host "=====================================" -ForegroundColor Cyan
    Write-Host "  预期输出示例" -ForegroundColor Cyan
    Write-Host "=====================================" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "控制台应该显示类似内容:" -ForegroundColor Yellow
    Write-Host "  === 订单数据调试 ===" -ForegroundColor Gray
    Write-Host "  完整响应: {data: {...}}" -ForegroundColor Gray
    Write-Host "  res.data: {code: 200, msg: '获取订单列表成功', ...}" -ForegroundColor Gray
    Write-Host "  res.data.code: 200" -ForegroundColor Gray
    Write-Host "  res.data.data: {data: Array(2)}" -ForegroundColor Gray
    Write-Host "  res.data.data.data: (2) [{...}, {...}]" -ForegroundColor Gray
    Write-Host "  ✅ 订单数据赋值成功" -ForegroundColor Gray
    Write-Host "  this.orders: (2) [{...}, {...}]" -ForegroundColor Gray
    Write-Host "  订单数量: 2" -ForegroundColor Gray
    Write-Host "  this.total: 2" -ForegroundColor Gray
    Write-Host "  当前页: 1 每页大小: 10" -ForegroundColor Gray
    Write-Host "  pagedOrders computed: (2) [{...}, {...}]" -ForegroundColor Gray
    Write-Host "  pagedOrders length: 2" -ForegroundColor Gray
    Write-Host "  `$nextTick - pagedOrders: (2) [{...}, {...}]" -ForegroundColor Gray
    Write-Host "  `$nextTick - pagedOrders length: 2" -ForegroundColor Gray
    Write-Host "  ==================" -ForegroundColor Gray
    Write-Host ""
    Write-Host "页面调试信息框应该显示:" -ForegroundColor Yellow
    Write-Host "  orders 数组长度: 2" -ForegroundColor Gray
    Write-Host "  pagedOrders 数组长度: 2" -ForegroundColor Gray
    Write-Host "  当前页: 1, 每页大小: 10, 总数: 2" -ForegroundColor Gray
    Write-Host ""
    Write-Host "=====================================" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "按任意键退出..." -ForegroundColor Yellow
    $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
    
} else {
    Write-Host "  ❌ 未找到 Chrome 浏览器" -ForegroundColor Red
    Write-Host ""
    Write-Host "请手动在浏览器中打开以下 URL:" -ForegroundColor Yellow
    Write-Host "  http://localhost:8080/test-api.html" -ForegroundColor White
    Write-Host "  http://localhost:8080/#/sales/orders" -ForegroundColor White
    Write-Host ""
    Write-Host "并按 F12 打开开发者工具查看控制台输出" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "按任意键退出..." -ForegroundColor Yellow
    $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
}

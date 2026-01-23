# ====================================
# 销售订单功能测试脚本
# ====================================

$baseUrl = "http://localhost:8090"
$token = ""

Write-Host "=====================================" -ForegroundColor Cyan
Write-Host "销售订单功能测试" -ForegroundColor Cyan
Write-Host "=====================================" -ForegroundColor Cyan
Write-Host ""

# 第一步：登录获取Token
Write-Host "[步骤 1/5] 登录系统..." -ForegroundColor Yellow
$loginBody = @{
    username = "admin"
    password = "123456"
} | ConvertTo-Json

try {
    $loginResponse = Invoke-RestMethod -Uri "$baseUrl/user/login" -Method POST -Body $loginBody -ContentType "application/json"
    
    if ($loginResponse.code -eq 20000) {
        $token = $loginResponse.data.token
        Write-Host "✓ 登录成功！Token: $($token.Substring(0, 20))..." -ForegroundColor Green
    } else {
        Write-Host "✗ 登录失败: $($loginResponse.msg)" -ForegroundColor Red
        exit 1
    }
} catch {
    Write-Host "✗ 登录请求失败: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

Write-Host ""

# 第二步：创建订单
Write-Host "[步骤 2/5] 创建测试订单..." -ForegroundColor Yellow

$createOrderBody = @{
    customer = "测试客户-PowerShell"
    customerOrderNo = "TEST-PS-001"
    orderDate = Get-Date -Format "yyyy-MM-dd"
    deliveryDate = (Get-Date).AddDays(7).ToString("yyyy-MM-dd")
    deliveryAddress = "广州市天河区测试路123号"
    items = @(
        @{
            materialCode = "MT-TEST-001"
            materialName = "测试胶带A"
            length = 1000
            width = 50
            thickness = 0.08
            rolls = 10
            unitPrice = 25.00
            remark = "测试备注1"
        },
        @{
            materialCode = "MT-TEST-002"
            materialName = "测试胶带B"
            length = 1200
            width = 60
            thickness = 0.10
            rolls = 5
            unitPrice = 30.00
            remark = "测试备注2"
        }
    )
} | ConvertTo-Json -Depth 10

$headers = @{
    "X-Token" = $token
    "Content-Type" = "application/json"
}

try {
    $createResponse = Invoke-RestMethod -Uri "$baseUrl/sales/orders" -Method POST -Body $createOrderBody -Headers $headers
    
    if ($createResponse.code -eq 200) {
        $createdOrder = $createResponse.data.data
        Write-Host "✓ 订单创建成功！" -ForegroundColor Green
        Write-Host "  订单号: $($createdOrder.orderNo)" -ForegroundColor Cyan
        Write-Host "  客户: $($createdOrder.customer)" -ForegroundColor Cyan
        Write-Host "  总金额: ¥$($createdOrder.totalAmount)" -ForegroundColor Cyan
        Write-Host "  总面积: $($createdOrder.totalArea) ㎡" -ForegroundColor Cyan
        $orderNo = $createdOrder.orderNo
    } else {
        Write-Host "✗ 创建订单失败: $($createResponse.msg)" -ForegroundColor Red
        exit 1
    }
} catch {
    Write-Host "✗ 创建订单请求失败: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "详细信息: $($_.ErrorDetails.Message)" -ForegroundColor Red
    exit 1
}

Write-Host ""

# 第三步：查询订单列表
Write-Host "[步骤 3/5] 查询订单列表..." -ForegroundColor Yellow

try {
    $listResponse = Invoke-RestMethod -Uri "$baseUrl/sales/orders" -Method GET -Headers $headers
    
    if ($listResponse.code -eq 200) {
        $orders = $listResponse.data.data
        Write-Host "✓ 查询成功！共 $($orders.Count) 个订单" -ForegroundColor Green
        
        if ($orders.Count -gt 0) {
            Write-Host "  最新订单:" -ForegroundColor Cyan
            $latestOrder = $orders[0]
            Write-Host "    订单号: $($latestOrder.orderNo)" -ForegroundColor White
            Write-Host "    客户: $($latestOrder.customer)" -ForegroundColor White
            Write-Host "    总金额: ¥$($latestOrder.totalAmount)" -ForegroundColor White
        }
    } else {
        Write-Host "✗ 查询订单失败: $($listResponse.msg)" -ForegroundColor Red
    }
} catch {
    Write-Host "✗ 查询订单请求失败: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host ""

# 第四步：查询订单详情
Write-Host "[步骤 4/5] 查询订单详情..." -ForegroundColor Yellow

try {
    $detailResponse = Invoke-RestMethod -Uri "$baseUrl/sales/orders/$orderNo" -Method GET -Headers $headers
    
    if ($detailResponse.code -eq 200) {
        $order = $detailResponse.data.data
        Write-Host "✓ 查询成功！" -ForegroundColor Green
        Write-Host "  订单号: $($order.orderNo)" -ForegroundColor Cyan
        Write-Host "  客户: $($order.customer)" -ForegroundColor Cyan
        Write-Host "  明细数量: $($order.items.Count)" -ForegroundColor Cyan
        
        foreach ($item in $order.items) {
            Write-Host "    - $($item.materialName): $($item.sqm)㎡ × ¥$($item.unitPrice) = ¥$($item.amount)" -ForegroundColor White
        }
    } else {
        Write-Host "✗ 查询订单详情失败: $($detailResponse.msg)" -ForegroundColor Red
    }
} catch {
    Write-Host "✗ 查询订单详情请求失败: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host ""

# 第五步：更新订单
Write-Host "[步骤 5/5] 更新订单（可选，按任意键继续或Ctrl+C跳过）..." -ForegroundColor Yellow
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")

$updateOrderBody = @{
    orderNo = $orderNo
    customer = "测试客户-PowerShell-已更新"
    customerOrderNo = "TEST-PS-001-UPDATED"
    orderDate = Get-Date -Format "yyyy-MM-dd"
    deliveryDate = (Get-Date).AddDays(10).ToString("yyyy-MM-dd")
    deliveryAddress = "深圳市南山区更新路456号"
    items = @(
        @{
            materialCode = "MT-TEST-003"
            materialName = "更新后的胶带"
            length = 1500
            width = 70
            thickness = 0.12
            rolls = 8
            unitPrice = 35.00
            remark = "更新后的备注"
        }
    )
} | ConvertTo-Json -Depth 10

try {
    $updateResponse = Invoke-RestMethod -Uri "$baseUrl/sales/orders" -Method PUT -Body $updateOrderBody -Headers $headers
    
    if ($updateResponse.code -eq 200) {
        $updatedOrder = $updateResponse.data.data
        Write-Host "✓ 订单更新成功！" -ForegroundColor Green
        Write-Host "  新客户名: $($updatedOrder.customer)" -ForegroundColor Cyan
        Write-Host "  新总金额: ¥$($updatedOrder.totalAmount)" -ForegroundColor Cyan
    } else {
        Write-Host "✗ 更新订单失败: $($updateResponse.msg)" -ForegroundColor Red
    }
} catch {
    Write-Host "✗ 更新订单请求失败: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host ""
Write-Host "=====================================" -ForegroundColor Cyan
Write-Host "测试完成！" -ForegroundColor Green
Write-Host "=====================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "提示: 可以在浏览器中访问 http://localhost:9527 查看订单详情" -ForegroundColor Yellow
Write-Host "      测试订单号: $orderNo" -ForegroundColor Yellow
Write-Host ""
Write-Host "删除测试数据的SQL (可选):" -ForegroundColor Gray
Write-Host "  DELETE FROM sales_order_items WHERE order_id IN (SELECT id FROM sales_orders WHERE order_no = '$orderNo');" -ForegroundColor Gray
Write-Host "  DELETE FROM sales_orders WHERE order_no = '$orderNo';" -ForegroundColor Gray
Write-Host ""

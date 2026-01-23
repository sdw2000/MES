# 快速诊断脚本 - 销售订单前端问题
# 自动检查后端 API 和数据结构

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  销售订单快速诊断工具" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 1. 检查服务器
Write-Host "[1/5] 检查服务器状态..." -ForegroundColor Yellow
$frontend = netstat -ano | findstr ":8080" | findstr "LISTENING"
$backend = netstat -ano | findstr ":8090" | findstr "LISTENING"

if ($frontend) {
    Write-Host "  ✅ 前端: http://localhost:8080" -ForegroundColor Green
} else {
    Write-Host "  ❌ 前端未运行" -ForegroundColor Red
    exit
}

if ($backend) {
    Write-Host "  ✅ 后端: http://localhost:8090" -ForegroundColor Green
} else {
    Write-Host "  ❌ 后端未运行" -ForegroundColor Red
    exit
}
Write-Host ""

# 2. 测试 API
Write-Host "[2/5] 测试后端 API..." -ForegroundColor Yellow

# 从配置文件读取 token（如果有）
$token = $null
$redisScript = @"
import redis
import json

try:
    r = redis.Redis(host='localhost', port=6379, db=0, decode_responses=True)
    keys = r.keys('token:*')
    if keys:
        token_data = r.get(keys[0])
        if token_data:
            data = json.loads(token_data)
            print(data.get('token', ''))
    else:
        print('')
except Exception as e:
    print('')
"@

try {
    $token = python -c $redisScript 2>$null
    if ($token -and $token.Trim() -ne '') {
        Write-Host "  ✅ 找到 token (从 Redis)" -ForegroundColor Green
    } else {
        Write-Host "  ⚠️  未找到 token，使用测试 token" -ForegroundColor Yellow
        $token = "test-token-12345"
    }
} catch {
    Write-Host "  ⚠️  无法从 Redis 读取，使用测试 token" -ForegroundColor Yellow
    $token = "test-token-12345"
}

# 调用 API
try {
    $headers = @{
        "Authorization" = $token
        "Content-Type" = "application/json"
    }
    
    $response = Invoke-RestMethod -Uri "http://localhost:8090/sales/orders" -Method GET -Headers $headers -TimeoutSec 5
    
    Write-Host "  ✅ API 调用成功" -ForegroundColor Green
    Write-Host ""
    
    # 3. 分析响应结构
    Write-Host "[3/5] 分析响应数据结构..." -ForegroundColor Yellow
    Write-Host "  响应码: $($response.code)" -ForegroundColor White
    Write-Host "  响应消息: $($response.msg)" -ForegroundColor White
    
    if ($response.data) {
        Write-Host "  data 字段: 存在 ✅" -ForegroundColor Green
        
        if ($response.data.data) {
            Write-Host "  data.data 字段: 存在 ✅" -ForegroundColor Green
            
            $orders = $response.data.data
            if ($orders -is [Array]) {
                Write-Host "  data.data 类型: 数组 ✅" -ForegroundColor Green
                Write-Host "  订单数量: $($orders.Count)" -ForegroundColor White
            } else {
                Write-Host "  data.data 类型: 非数组 ❌" -ForegroundColor Red
            }
        } else {
            Write-Host "  data.data 字段: 不存在 ❌" -ForegroundColor Red
        }
    } else {
        Write-Host "  data 字段: 不存在 ❌" -ForegroundColor Red
    }
    Write-Host ""
    
    # 4. 检查订单数据
    Write-Host "[4/5] 检查订单数据..." -ForegroundColor Yellow
    
    if ($orders -and $orders.Count -gt 0) {
        Write-Host "  找到 $($orders.Count) 个订单" -ForegroundColor Green
        Write-Host ""
        
        for ($i = 0; $i -lt $orders.Count; $i++) {
            $order = $orders[$i]
            Write-Host "  订单 #$($i+1):" -ForegroundColor Cyan
            Write-Host "    ID: $($order.id)" -ForegroundColor White
            Write-Host "    订单号: $($order.orderNo)" -ForegroundColor White
            Write-Host "    客户: $($order.customer)" -ForegroundColor White
            Write-Host "    总金额: $($order.totalAmount)" -ForegroundColor White
            Write-Host "    总面积: $($order.totalArea)" -ForegroundColor White
            Write-Host "    下单日期: $($order.orderDate)" -ForegroundColor White
            Write-Host "    交货日期: $($order.deliveryDate)" -ForegroundColor White
            
            if ($order.items) {
                Write-Host "    明细数量: $($order.items.Count) 条 ✅" -ForegroundColor Green
            } else {
                Write-Host "    明细数量: 0 条 ⚠️" -ForegroundColor Yellow
            }
            Write-Host ""
        }
    } else {
        Write-Host "  ❌ 没有订单数据" -ForegroundColor Red
    }
    
    # 5. 生成诊断报告
    Write-Host "[5/5] 生成诊断报告..." -ForegroundColor Yellow
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host "  诊断结果" -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host ""
    
    # 检查前端代码预期的数据结构
    $expectedStructure = "res.data.data.data"
    Write-Host "前端代码预期数据路径: $expectedStructure" -ForegroundColor Yellow
    Write-Host ""
    
    # 检查是否匹配
    if ($response.data -and $response.data.data -and ($response.data.data -is [Array])) {
        Write-Host "✅ 数据结构匹配！" -ForegroundColor Green
        Write-Host "   前端应该能够正确读取数据" -ForegroundColor Green
        Write-Host ""
        Write-Host "如果前端仍然不显示，可能的原因：" -ForegroundColor Yellow
        Write-Host "  1. Vue 组件没有正确挂载" -ForegroundColor White
        Write-Host "  2. computed 属性 pagedOrders 有问题" -ForegroundColor White
        Write-Host "  3. Element UI 表格渲染失败" -ForegroundColor White
        Write-Host "  4. 路由或权限问题" -ForegroundColor White
    } else {
        Write-Host "❌ 数据结构不匹配！" -ForegroundColor Red
        Write-Host "   需要修改前端代码的数据访问路径" -ForegroundColor Red
    }
    Write-Host ""
    
    # 显示完整响应（JSON）
    Write-Host "完整 API 响应（JSON）：" -ForegroundColor Cyan
    Write-Host "----------------------------------------" -ForegroundColor Gray
    $response | ConvertTo-Json -Depth 10 | Write-Host -ForegroundColor Gray
    Write-Host "----------------------------------------" -ForegroundColor Gray
    Write-Host ""
    
    # 保存到文件
    $reportPath = "e:\java\MES\api-diagnostic-report.json"
    $response | ConvertTo-Json -Depth 10 | Out-File -FilePath $reportPath -Encoding UTF8
    Write-Host "📄 完整报告已保存到: $reportPath" -ForegroundColor Green
    Write-Host ""
    
    # 下一步建议
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host "  下一步操作建议" -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "1. 打开浏览器访问订单页面：" -ForegroundColor Yellow
    Write-Host "   http://localhost:8080/#/sales/orders" -ForegroundColor White
    Write-Host ""
    Write-Host "2. 按 F12 打开开发者工具，查看 Console 标签" -ForegroundColor Yellow
    Write-Host "   应该看到 '=== 订单数据调试 ===' 输出" -ForegroundColor White
    Write-Host ""
    Write-Host "3. 检查页面顶部的调试信息框" -ForegroundColor Yellow
    Write-Host "   应该显示: orders 数组长度: $($orders.Count)" -ForegroundColor White
    Write-Host ""
    Write-Host "4. 如果看到数据但表格为空，检查：" -ForegroundColor Yellow
    Write-Host "   - Elements 标签中是否有 <tr> 元素" -ForegroundColor White
    Write-Host "   - 是否有 CSS 隐藏了表格行" -ForegroundColor White
    Write-Host "   - 是否有 JavaScript 错误" -ForegroundColor White
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Cyan
    
} catch {
    Write-Host "  ❌ API 调用失败" -ForegroundColor Red
    Write-Host "  错误: $_" -ForegroundColor Red
    Write-Host ""
    Write-Host "可能的原因：" -ForegroundColor Yellow
    Write-Host "  1. 后端服务未完全启动" -ForegroundColor White
    Write-Host "  2. Token 无效或已过期" -ForegroundColor White
    Write-Host "  3. 数据库连接问题" -ForegroundColor White
    Write-Host "  4. 权限配置问题" -ForegroundColor White
}

Write-Host ""
Write-Host "按任意键退出..." -ForegroundColor Yellow
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")

# 简单 API 测试
Write-Host "测试销售订单 API..." -ForegroundColor Cyan

try {
    $headers = @{
        'Authorization' = 'Bearer test-token-123'
        'Content-Type' = 'application/json'
    }
    
    $uri = 'http://localhost:8090/sales/orders'
    Write-Host "请求 URL: $uri" -ForegroundColor Yellow
    
    $response = Invoke-WebRequest -Uri $uri -Headers $headers -UseBasicParsing
    
    Write-Host "`n状态码: $($response.StatusCode)" -ForegroundColor Green
    Write-Host "`n响应内容:" -ForegroundColor Yellow
    $response.Content | Write-Host
    
} catch {
    Write-Host "`n错误: $_" -ForegroundColor Red
    if ($_.Exception.Response) {
        Write-Host "状态码: $($_.Exception.Response.StatusCode.value__)" -ForegroundColor Red
    }
}

Write-Host "`n按任意键退出..."
$null = Read-Host

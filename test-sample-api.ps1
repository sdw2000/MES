# 测试样品管理API
Write-Host "测试样品管理API..." -ForegroundColor Cyan

try {
    $response = Invoke-WebRequest -Uri "http://localhost:8090/api/sales/samples" -Method GET -Headers @{"X-Token"="test"} -UseBasicParsing -ErrorAction Stop
    Write-Host "成功! 状态码: $($response.StatusCode)" -ForegroundColor Green
    Write-Host "响应内容:" -ForegroundColor Yellow
    $response.Content
} catch {
    Write-Host "失败! 错误信息:" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
    
    if ($_.ErrorDetails) {
        Write-Host "`n详细错误:" -ForegroundColor Yellow
        Write-Host $_.ErrorDetails.Message
    }
}

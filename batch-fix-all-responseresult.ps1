# 批量修复所有Service和ServiceImpl中的ResponseResult泛型问题

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "  批量修复ResponseResult泛型问题" -ForegroundColor Green  
Write-Host "========================================`n" -ForegroundColor Cyan

$fixCount = 0

# 定义要修复的文件列表
$files = @(
    "e:\java\MES\src\main\java\com\fine\service\SalesOrderService.java",
    "e:\java\MES\src\main\java\com\fine\service\QuotationService.java",
    "e:\java\MES\src\main\java\com\fine\service\OrderService.java",
    "e:\java\MES\src\main\java\com\fine\service\LoginServcie.java",
    "e:\java\MES\src\main\java\com\fine\serviceIMPL\SalesOrderServiceImpl.java",
    "e:\java\MES\src\main\java\com\fine\serviceIMPL\QuotationServiceImpl.java",
    "e:\java\MES\src\main\java\com\fine\serviceIMPL\OrderServiceImpl.java",
    "e:\java\MES\src\main\java\com\fine\serviceIMPL\TapeInventoryImpl.java",
    "e:\java\MES\src\main\java\com\fine\controller\QuotationDetailsController.java",
    "e:\java\MES\src\main\java\com\fine\controller\QuotationController.java",
    "e:\java\MES\src\main\java\com\fine\controller\OrderController.java",
    "e:\java\MES\src\main\java\com\fine\controller\ContactController.java",
    "e:\java\MES\src\main\java\com\fine\controller\LoginController.java",
    "e:\java\MES\src\main\java\com\fine\controller\TapeMinController.java",
    "e:\java\MES\src\main\java\com\fine\controller\TapeInventoryController.java"
)

foreach ($file in $files) {
    if (Test-Path $file) {
        Write-Host "处理: $file" -ForegroundColor Cyan
        $content = Get-Content $file -Raw
        
        # 替换方法返回类型 ResponseResult xxx( -> ResponseResult<?> xxx(
        # 这是一个通用的修复，后续可能需要手动指定具体类型
        $originalContent = $content
        
        # 替换所有 new ResponseResult( 为 new ResponseResult<>(
        $content = $content -replace 'new ResponseResult\s*\(', 'new ResponseResult<>('
        
        # 替换方法签名中的 ResponseResult（不带泛型）
        # 匹配：public/private ResponseResult methodName(
        $content = $content -replace '(\b(?:public|private|protected)\s+)ResponseResult\s+([a-zA-Z0-9_]+)\s*\(', '$1ResponseResult<?> $2('
        
        if ($content -ne $originalContent) {
            Set-Content $file -Value $content -NoNewline -Encoding UTF8
            Write-Host "  ✓ 已修复" -ForegroundColor Green
            $fixCount++
        } else {
            Write-Host "  - 无需修复" -ForegroundColor Gray
        }
    } else {
        Write-Host "  ✗ 文件不存在: $file" -ForegroundColor Yellow
    }
}

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "  修复完成！共修复 $fixCount 个文件" -ForegroundColor Green
Write-Host "========================================`n" -ForegroundColor Cyan

Write-Host "注意：所有文件已使用通配符 <?> 修复" -ForegroundColor Yellow
Write-Host "建议手动检查并指定具体的泛型类型！`n" -ForegroundColor Yellow

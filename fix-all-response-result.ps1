# 批量修复所有Controller中的ResponseResult泛型问题

Write-Host "开始批量修复ResponseResult泛型问题..." -ForegroundColor Green

$files = @(
    "e:\java\MES\src\main\java\com\fine\controller\TapeQuotationController.java",
    "e:\java\MES\src\main\java\com\fine\controller\QuotationDetailsController.java",
    "e:\java\MES\src\main\java\com\fine\controller\SalesOrderController.java"
)

foreach ($file in $files) {
    if (Test-Path $file) {
        Write-Host "处理文件: $file" -ForegroundColor Cyan
        $content = Get-Content $file -Raw
        
        # 替换 new ResponseResult( 为 new ResponseResult<>(
        $content = $content -replace 'new ResponseResult\(', 'new ResponseResult<>('
        
        # 替换 public ResponseResult xxx( 为 public ResponseResult<?> xxx(
        # 这个需要手动指定具体类型，所以我们先用通配符
        $content = $content -replace 'public ResponseResult ([a-zA-Z]+)\(', 'public ResponseResult<?> $1('
        
        Set-Content $file -Value $content -NoNewline
        Write-Host "✓ 完成: $file" -ForegroundColor Green
    } else {
        Write-Host "✗ 文件不存在: $file" -ForegroundColor Red
    }
}

Write-Host "`n所有文件处理完成！" -ForegroundColor Green
Write-Host "注意: 部分文件可能需要手动指定具体的泛型类型！" -ForegroundColor Yellow

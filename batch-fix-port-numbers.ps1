# ========================================
#  批量更正端口号脚本
#  将所有文档中的 9527 端口改为 8080
# ========================================

Write-Host "================================================" -ForegroundColor Cyan
Write-Host "  批量端口号更正工具" -ForegroundColor Cyan
Write-Host "  9527 → 8080" -ForegroundColor Cyan
Write-Host "================================================" -ForegroundColor Cyan
Write-Host ""

# 定义需要更正的文件列表
$files = @(
    "E:\java\MES\SALES-ORDER-SUMMARY.md",
    "E:\java\MES\SALES-ORDER-README.md",
    "E:\java\MES\SALES-ORDER-QUICKSTART.md",
    "E:\java\MES\SALES-ORDER-IMPLEMENTATION.md",
    "E:\java\MES\SALES-ORDER-COMPLETION-REPORT.md",
    "E:\java\MES\TIMEOUT-DIAGNOSIS.md",
    "E:\java\MES\STARTUP-GUIDE.md",
    "E:\java\MES\ROLLBACK-COMPLETE.md"
)

# 统计信息
$totalFiles = 0
$totalReplacements = 0
$failedFiles = @()

Write-Host "开始处理文件..." -ForegroundColor Yellow
Write-Host ""

foreach ($file in $files) {
    if (Test-Path $file) {
        Write-Host "处理: " -NoNewline
        Write-Host $file -ForegroundColor White
        
        try {
            # 读取文件内容
            $content = Get-Content -Path $file -Raw -Encoding UTF8
            
            # 统计替换次数
            $count = ([regex]::Matches($content, "9527")).Count
            
            if ($count -gt 0) {
                # 执行替换
                $newContent = $content -replace "9527", "8080"
                
                # 保存文件
                Set-Content -Path $file -Value $newContent -Encoding UTF8 -NoNewline
                
                Write-Host "  ✓ 替换了 $count 处" -ForegroundColor Green
                $totalFiles++
                $totalReplacements += $count
            } else {
                Write-Host "  - 未发现需要替换的内容" -ForegroundColor Gray
            }
        } catch {
            Write-Host "  ✗ 处理失败: $_" -ForegroundColor Red
            $failedFiles += $file
        }
    } else {
        Write-Host "跳过: $file (文件不存在)" -ForegroundColor Yellow
    }
    Write-Host ""
}

# 显示总结
Write-Host "================================================" -ForegroundColor Cyan
Write-Host "  处理完成" -ForegroundColor Cyan
Write-Host "================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "成功处理文件数: " -NoNewline
Write-Host $totalFiles -ForegroundColor Green
Write-Host "总替换次数: " -NoNewline
Write-Host $totalReplacements -ForegroundColor Green

if ($failedFiles.Count -gt 0) {
    Write-Host ""
    Write-Host "失败文件列表:" -ForegroundColor Red
    foreach ($file in $failedFiles) {
        Write-Host "  - $file" -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "================================================" -ForegroundColor Cyan
Write-Host ""

# 提示后续操作
Write-Host "📌 重要提示:" -ForegroundColor Yellow
Write-Host ""
Write-Host "1. 前端README文件未自动更新，请手动检查:" -ForegroundColor White
Write-Host "   E:\vue\ERP\README.zh-CN.md" -ForegroundColor Gray
Write-Host "   E:\vue\ERP\README.md" -ForegroundColor Gray
Write-Host "   E:\vue\ERP\README.ja.md" -ForegroundColor Gray
Write-Host "   E:\vue\ERP\README.es.md" -ForegroundColor Gray
Write-Host ""
Write-Host "2. 如果后端有CORS配置，请检查是否需要更新:" -ForegroundColor White
Write-Host "   configuration.addAllowedOrigin(`"http://localhost:8080`");" -ForegroundColor Gray
Write-Host ""
Write-Host "3. 验证端口配置:" -ForegroundColor White
Write-Host "   netstat -ano | findstr `":8080 :8090`"" -ForegroundColor Gray
Write-Host ""

# 询问是否处理前端README
Write-Host "是否需要处理前端README文件? (Y/N): " -NoNewline -ForegroundColor Yellow
$response = Read-Host

if ($response -eq "Y" -or $response -eq "y") {
    Write-Host ""
    Write-Host "处理前端README文件..." -ForegroundColor Yellow
    
    $frontendReadmeFiles = @(
        "E:\vue\ERP\README.zh-CN.md",
        "E:\vue\ERP\README.md",
        "E:\vue\ERP\README.ja.md",
        "E:\vue\ERP\README.es.md"
    )
    
    foreach ($file in $frontendReadmeFiles) {
        if (Test-Path $file) {
            Write-Host "处理: $file" -ForegroundColor White
            
            try {
                $content = Get-Content -Path $file -Raw -Encoding UTF8
                $count = ([regex]::Matches($content, "9527")).Count
                
                if ($count -gt 0) {
                    $newContent = $content -replace "9527", "8080"
                    Set-Content -Path $file -Value $newContent -Encoding UTF8 -NoNewline
                    Write-Host "  ✓ 替换了 $count 处" -ForegroundColor Green
                } else {
                    Write-Host "  - 未发现需要替换的内容" -ForegroundColor Gray
                }
            } catch {
                Write-Host "  ✗ 处理失败: $_" -ForegroundColor Red
            }
        }
        Write-Host ""
    }
    
    Write-Host "前端README处理完成！" -ForegroundColor Green
} else {
    Write-Host ""
    Write-Host "已跳过前端README文件处理" -ForegroundColor Gray
}

Write-Host ""
Write-Host "全部完成！✓" -ForegroundColor Green
Write-Host ""

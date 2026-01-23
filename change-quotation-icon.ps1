# 报价单图标快速切换脚本
# 用途：快速尝试不同图标效果

Write-Host "================================" -ForegroundColor Cyan
Write-Host "  报价单图标选择工具" -ForegroundColor Yellow
Write-Host "================================" -ForegroundColor Cyan
Write-Host ""

$routerFile = "E:\vue\ERP\src\router\index.js"

Write-Host "📋 推荐图标（按推荐度排序）：" -ForegroundColor Green
Write-Host ""
Write-Host "1. clipboard  ⭐⭐⭐⭐⭐ (强烈推荐)" -ForegroundColor Yellow
Write-Host "   含义: 剪贴板/文档清单" -ForegroundColor Gray
Write-Host "   特点: 最符合报价单业务场景" -ForegroundColor Gray
Write-Host ""
Write-Host "2. money      ⭐⭐⭐⭐" -ForegroundColor Yellow
Write-Host "   含义: 金钱/价格 (￥)" -ForegroundColor Gray
Write-Host "   特点: 突出价格属性" -ForegroundColor Gray
Write-Host ""
Write-Host "3. form       ⭐⭐⭐⭐" -ForegroundColor Yellow
Write-Host "   含义: 表单/文档编辑" -ForegroundColor Gray
Write-Host "   特点: 强调可编辑性" -ForegroundColor Gray
Write-Host ""
Write-Host "4. edit       ⭐⭐⭐" -ForegroundColor Yellow
Write-Host "   含义: 编辑" -ForegroundColor Gray
Write-Host "   特点: 简洁通用" -ForegroundColor Gray
Write-Host ""
Write-Host "5. excel      ⭐⭐⭐" -ForegroundColor Yellow
Write-Host "   含义: Excel表格" -ForegroundColor Gray
Write-Host "   特点: 适合数据类单据" -ForegroundColor Gray
Write-Host ""

Write-Host "当前图标配置：" -ForegroundColor Cyan
$currentConfig = Get-Content $routerFile | Select-String "报价单管理.*icon" | Select-Object -First 1
if ($currentConfig) {
    Write-Host "  $currentConfig" -ForegroundColor White
    if ($currentConfig -match "clipboard") {
        Write-Host "  ✅ 当前使用: clipboard (推荐图标)" -ForegroundColor Green
    } elseif ($currentConfig -match "money") {
        Write-Host "  ✅ 当前使用: money" -ForegroundColor Green
    } elseif ($currentConfig -match "form") {
        Write-Host "  ✅ 当前使用: form" -ForegroundColor Green
    } else {
        Write-Host "  ⚠️  当前图标可以优化" -ForegroundColor Yellow
    }
}
Write-Host ""

Write-Host "是否要切换图标？" -ForegroundColor Cyan
Write-Host "1 = clipboard (推荐)" -ForegroundColor White
Write-Host "2 = money" -ForegroundColor White
Write-Host "3 = form" -ForegroundColor White
Write-Host "4 = edit" -ForegroundColor White
Write-Host "5 = excel" -ForegroundColor White
Write-Host "0 = 保持当前" -ForegroundColor White
Write-Host ""

$choice = Read-Host "请选择 (0-5)"

$iconMap = @{
    "1" = @{ name = "clipboard"; desc = "剪贴板/文档清单" }
    "2" = @{ name = "money"; desc = "金钱/价格" }
    "3" = @{ name = "form"; desc = "表单/文档编辑" }
    "4" = @{ name = "edit"; desc = "编辑" }
    "5" = @{ name = "excel"; desc = "Excel表格" }
}

if ($choice -eq "0") {
    Write-Host ""
    Write-Host "✅ 保持当前图标" -ForegroundColor Green
    exit 0
}

if ($iconMap.ContainsKey($choice)) {
    $newIcon = $iconMap[$choice].name
    $iconDesc = $iconMap[$choice].desc
    
    Write-Host ""
    Write-Host "正在切换图标为: $newIcon ($iconDesc)" -ForegroundColor Cyan
    
    # 读取文件内容
    $content = Get-Content $routerFile -Raw
    
    # 替换图标
    $pattern = "(报价单管理.*icon:\s*')([^']+)(')"
    $replacement = "`${1}$newIcon`${3}"
    $newContent = $content -replace $pattern, $replacement
    
    # 写回文件
    Set-Content $routerFile -Value $newContent -Encoding UTF8
    
    Write-Host "✅ 图标已更新为: $newIcon" -ForegroundColor Green
    Write-Host ""
    Write-Host "📝 下一步操作：" -ForegroundColor Yellow
    Write-Host "  1. 重启前端服务 (如果正在运行)" -ForegroundColor White
    Write-Host "     命令: 在前端窗口按 Ctrl+C 停止" -ForegroundColor Gray
    Write-Host "     命令: npm run dev" -ForegroundColor Gray
    Write-Host "  2. 刷新浏览器 (Ctrl+Shift+R)" -ForegroundColor White
    Write-Host "  3. 查看新图标效果" -ForegroundColor White
    Write-Host ""
    
    # 询问是否重启前端
    $restart = Read-Host "是否现在重启前端服务？(y/n)"
    if ($restart -eq 'y' -or $restart -eq 'Y') {
        Write-Host ""
        Write-Host "提示: 请先在前端服务窗口按 Ctrl+C 停止服务" -ForegroundColor Yellow
        Write-Host "然后按任意键在新窗口启动..." -ForegroundColor Yellow
        $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
        
        Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd E:\vue\ERP; Write-Host '启动前端服务...' -ForegroundColor Green; npm run dev"
        Write-Host "✅ 前端启动命令已执行（新窗口）" -ForegroundColor Green
    }
} else {
    Write-Host ""
    Write-Host "❌ 无效的选择" -ForegroundColor Red
}

Write-Host ""
Write-Host "完成！" -ForegroundColor Green

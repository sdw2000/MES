# 报价单系统 - 快速测试脚本

Write-Host "===============================================" -ForegroundColor Cyan
Write-Host "   报价单管理系统 - 快速测试" -ForegroundColor Cyan
Write-Host "===============================================" -ForegroundColor Cyan
Write-Host ""

# 测试文件是否存在
Write-Host "📋 检查文件..." -ForegroundColor Yellow

$files = @(
    "E:\java\MES\src\main\java\com\fine\modle\Quotation.java",
    "E:\java\MES\src\main\java\com\fine\modle\QuotationItem.java",
    "E:\java\MES\src\main\java\com\fine\Dao\QuotationMapper.java",
    "E:\java\MES\src\main\java\com\fine\Dao\QuotationItemMapper.java",
    "E:\java\MES\src\main\java\com\fine\service\QuotationService.java",
    "E:\java\MES\src\main\java\com\fine\serviceIMPL\QuotationServiceImpl.java",
    "E:\java\MES\src\main\java\com\fine\controller\QuotationController.java",
    "E:\vue\ERP\src\api\quotation.js",
    "E:\vue\ERP\src\views\sales\quotations.vue",
    "E:\java\MES\database-quotations.sql"
)

$allFilesExist = $true
foreach ($file in $files) {
    if (Test-Path $file) {
        Write-Host "  ✅ $([System.IO.Path]::GetFileName($file))" -ForegroundColor Green
    } else {
        Write-Host "  ❌ $([System.IO.Path]::GetFileName($file)) - 文件不存在" -ForegroundColor Red
        $allFilesExist = $false
    }
}

Write-Host ""

if ($allFilesExist) {
    Write-Host "✅ 所有文件检查通过！" -ForegroundColor Green
} else {
    Write-Host "❌ 有文件缺失，请检查！" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "📊 文件统计..." -ForegroundColor Yellow

# 统计代码行数
$javaFiles = Get-ChildItem -Path "E:\java\MES\src\main\java\com\fine" -Recurse -Filter "*Quotation*.java" -File
$javaLines = ($javaFiles | Get-Content | Measure-Object -Line).Lines

$vueFiles = Get-ChildItem -Path "E:\vue\ERP\src" -Recurse -Filter "*quotation*.vue" -File
$vueLines = ($vueFiles | Get-Content | Measure-Object -Line).Lines

$apiFiles = Get-ChildItem -Path "E:\vue\ERP\src\api" -Recurse -Filter "*quotation*.js" -File
$apiLines = ($apiFiles | Get-Content | Measure-Object -Line).Lines

Write-Host "  后端Java代码：$($javaFiles.Count) 个文件，约 $javaLines 行" -ForegroundColor Cyan
Write-Host "  前端Vue代码：$($vueFiles.Count) 个文件，约 $vueLines 行" -ForegroundColor Cyan
Write-Host "  前端API代码：$($apiFiles.Count) 个文件，约 $apiLines 行" -ForegroundColor Cyan

Write-Host ""
Write-Host "📚 文档检查..." -ForegroundColor Yellow

$docs = @(
    "E:\java\MES\QUOTATION-README.md",
    "E:\java\MES\QUOTATION-QUICKSTART.md",
    "E:\java\MES\QUOTATION-SUMMARY.md",
    "E:\java\MES\QUOTATION-IMPLEMENTATION-COMPLETE.md",
    "E:\java\MES\QUOTATION-FINAL-SUMMARY.md"
)

foreach ($doc in $docs) {
    if (Test-Path $doc) {
        $lines = (Get-Content $doc | Measure-Object -Line).Lines
        Write-Host "  ✅ $([System.IO.Path]::GetFileName($doc)) - $lines 行" -ForegroundColor Green
    } else {
        Write-Host "  ❌ $([System.IO.Path]::GetFileName($doc)) - 不存在" -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "===============================================" -ForegroundColor Cyan
Write-Host "   ✅ 报价单系统文件检查完成！" -ForegroundColor Green
Write-Host "===============================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "下一步：" -ForegroundColor Yellow
Write-Host "1. 初始化数据库: .\setup-quotation-database.ps1" -ForegroundColor White
Write-Host "2. 编译后端: mvn clean compile" -ForegroundColor White
Write-Host "3. 启动后端: mvn spring-boot:run" -ForegroundColor White
Write-Host "4. 启动前端: cd E:\vue\ERP; npm run dev" -ForegroundColor White
Write-Host "5. 访问系统: http://localhost:8080" -ForegroundColor White
Write-Host ""

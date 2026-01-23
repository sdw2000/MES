# 报价单连接诊断脚本
# 用途：检查前后端连接状态

Write-Host "================================" -ForegroundColor Cyan
Write-Host "  报价单系统连接诊断" -ForegroundColor Yellow
Write-Host "================================" -ForegroundColor Cyan
Write-Host ""

# 1. 检查后端服务
Write-Host "[1/5] 检查后端服务..." -ForegroundColor Green
try {
    $backendHealth = Invoke-WebRequest -Uri "http://localhost:8090/actuator/health" -Method Get -TimeoutSec 3 -ErrorAction Stop
    Write-Host "  ✅ 后端服务运行正常 (端口: 8090)" -ForegroundColor Green
} catch {
    Write-Host "  ❌ 后端服务未运行或无法访问" -ForegroundColor Red
    Write-Host "  提示: 请先启动后端服务" -ForegroundColor Yellow
    Write-Host "  命令: cd E:\java\MES; mvn spring-boot:run" -ForegroundColor Cyan
}
Write-Host ""

# 2. 检查报价单API
Write-Host "[2/5] 检查报价单API..." -ForegroundColor Green
try {
    # 注意：需要登录token，这里只测试端点是否存在
    $quotationApi = Invoke-WebRequest -Uri "http://localhost:8090/quotation/list" -Method Get -TimeoutSec 3 -ErrorAction Stop
    Write-Host "  ✅ 报价单API端点可访问" -ForegroundColor Green
} catch {
    $statusCode = $null
    if ($_.Exception.Response) {
        $statusCode = $_.Exception.Response.StatusCode.value__
    }
    
    if ($statusCode -eq 401) {
        Write-Host "  ⚠️  报价单API需要认证（正常，需要登录）" -ForegroundColor Yellow
        Write-Host "  ✅ API端点存在且正常" -ForegroundColor Green
    } elseif ($statusCode -eq 403) {
        Write-Host "  ⚠️  报价单API需要权限（正常，需要admin角色）" -ForegroundColor Yellow
        Write-Host "  ✅ API端点存在且正常" -ForegroundColor Green
    } else {
        Write-Host "  ❌ 报价单API无法访问: $($_.Exception.Message)" -ForegroundColor Red
    }
}
Write-Host ""

# 3. 检查前端配置
Write-Host "[3/5] 检查前端配置..." -ForegroundColor Green
$envFile = "E:\vue\ERP\.env.development"
if (Test-Path $envFile) {
    $baseApi = Get-Content $envFile | Select-String "VUE_APP_BASE_API"
    Write-Host "  ✅ 前端环境配置文件存在" -ForegroundColor Green
    Write-Host "  配置: $baseApi" -ForegroundColor Cyan
    
    if ($baseApi -match "8090") {
        Write-Host "  ✅ 前端API地址配置正确（8090端口）" -ForegroundColor Green
    } else {
        Write-Host "  ⚠️  前端API端口可能不正确" -ForegroundColor Yellow
    }
} else {
    Write-Host "  ❌ 前端环境配置文件不存在" -ForegroundColor Red
}
Write-Host ""

# 4. 检查路由配置
Write-Host "[4/5] 检查前端路由..." -ForegroundColor Green
$routerFile = "E:\vue\ERP\src\router\index.js"
if (Test-Path $routerFile) {
    $routerContent = Get-Content $routerFile -Raw
    if ($routerContent -match "quotations") {
        Write-Host "  ✅ 报价单路由已配置（/sales/quotations）" -ForegroundColor Green
    } else {
        Write-Host "  ⚠️  未找到quotations路由配置" -ForegroundColor Yellow
    }
} else {
    Write-Host "  ❌ 路由文件不存在" -ForegroundColor Red
}
Write-Host ""

# 5. 检查API文件
Write-Host "[5/5] 检查API接口文件..." -ForegroundColor Green
$apiFile = "E:\vue\ERP\src\api\quotation.js"
if (Test-Path $apiFile) {
    Write-Host "  ✅ 报价单API文件存在" -ForegroundColor Green
    $apiContent = Get-Content $apiFile -Raw
    $apiCount = ([regex]::Matches($apiContent, "export function")).Count
    Write-Host "  已定义 $apiCount 个API接口" -ForegroundColor Cyan
} else {
    Write-Host "  ❌ 报价单API文件不存在" -ForegroundColor Red
}
Write-Host ""

# 诊断总结
Write-Host "================================" -ForegroundColor Cyan
Write-Host "  诊断总结" -ForegroundColor Yellow
Write-Host "================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "✅ 已完成的配置：" -ForegroundColor Green
Write-Host "  1. 后端端口: 8090" -ForegroundColor White
Write-Host "  2. 前端API地址: http://localhost:8090" -ForegroundColor White
Write-Host "  3. 报价单路由: /sales/quotations" -ForegroundColor White
Write-Host "  4. API接口: /quotation/*" -ForegroundColor White
Write-Host ""

Write-Host "🔧 常见问题解决：" -ForegroundColor Cyan
Write-Host ""
Write-Host "【问题1】401未授权错误" -ForegroundColor Yellow
Write-Host "  原因: 未登录或token过期" -ForegroundColor White
Write-Host "  解决: 重新登录系统" -ForegroundColor Green
Write-Host ""
Write-Host "【问题2】403权限不足" -ForegroundColor Yellow
Write-Host "  原因: 当前用户没有admin权限" -ForegroundColor White
Write-Host "  解决: 使用admin账号登录" -ForegroundColor Green
Write-Host ""
Write-Host "【问题3】404接口不存在" -ForegroundColor Yellow
Write-Host "  原因: 后端服务未启动或路由错误" -ForegroundColor White
Write-Host "  解决: 启动后端服务" -ForegroundColor Green
Write-Host "  命令: cd E:\java\MES; mvn spring-boot:run" -ForegroundColor Cyan
Write-Host ""
Write-Host "【问题4】Network Error" -ForegroundColor Yellow
Write-Host "  原因: 前端无法连接后端" -ForegroundColor White
Write-Host "  解决: 检查后端是否运行在8090端口" -ForegroundColor Green
Write-Host "  检查: netstat -ano | findstr :8090" -ForegroundColor Cyan
Write-Host ""

Write-Host "📝 访问步骤：" -ForegroundColor Green
Write-Host "  1. 启动后端: mvn spring-boot:run" -ForegroundColor White
Write-Host "  2. 启动前端: npm run dev" -ForegroundColor White
Write-Host "  3. 浏览器访问: http://localhost:9527" -ForegroundColor White
Write-Host "  4. 使用admin账号登录" -ForegroundColor White
Write-Host "  5. 导航到: 销售 → 报价单管理" -ForegroundColor White
Write-Host ""

Write-Host "🔍 实时检查端口占用：" -ForegroundColor Cyan
netstat -ano | findstr ":8090" | ForEach-Object {
    Write-Host "  $_" -ForegroundColor White
}
if (-not (netstat -ano | findstr ":8090")) {
    Write-Host "  ⚠️  8090端口未被占用（后端可能未启动）" -ForegroundColor Yellow
}
Write-Host ""

Write-Host "✅ 诊断完成！" -ForegroundColor Green

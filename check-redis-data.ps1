# Redis数据查看脚本
# 使用方法: .\check-redis-data.ps1

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "   Redis数据查看工具" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$redisCliPath = "D:\360安全浏览器下载\Redis-8.4.0-Windows-x64-cygwin\redis-cli.exe"

if (!(Test-Path $redisCliPath)) {
    Write-Host "❌ 未找到redis-cli.exe" -ForegroundColor Red
    Write-Host "请检查路径: $redisCliPath" -ForegroundColor Yellow
    exit 1
}

Write-Host "1. 测试Redis连接..." -ForegroundColor Yellow
$ping = & $redisCliPath -h 127.0.0.1 -p 6379 ping 2>$null
if ($ping -ne "PONG") {
    Write-Host "   ❌ Redis未运行" -ForegroundColor Red
    exit 1
}
Write-Host "   ✅ Redis连接正常" -ForegroundColor Green
Write-Host ""

Write-Host "2. 查询所有login相关的键..." -ForegroundColor Yellow
$keys = & $redisCliPath -h 127.0.0.1 -p 6379 keys "login:*"
if ($keys) {
    Write-Host "   找到 $($keys.Count) 个键:" -ForegroundColor Green
    $keys | ForEach-Object {
        Write-Host "   📌 $_" -ForegroundColor Cyan
    }
} else {
    Write-Host "   ⚠️  未找到任何login:*键" -ForegroundColor Yellow
    Write-Host "   提示: 可能还没有用户登录" -ForegroundColor Gray
}
Write-Host ""

Write-Host "3. 查询所有键..." -ForegroundColor Yellow
$allKeys = & $redisCliPath -h 127.0.0.1 -p 6379 keys "*"
if ($allKeys) {
    Write-Host "   Redis中共有 $($allKeys.Count) 个键" -ForegroundColor Cyan
    if ($allKeys.Count -le 20) {
        Write-Host "   所有键列表:" -ForegroundColor Gray
        $allKeys | ForEach-Object {
            Write-Host "   - $_" -ForegroundColor Gray
        }
    } else {
        Write-Host "   (键太多，只显示前20个)" -ForegroundColor Gray
        $allKeys | Select-Object -First 20 | ForEach-Object {
            Write-Host "   - $_" -ForegroundColor Gray
        }
    }
} else {
    Write-Host "   Redis是空的" -ForegroundColor Yellow
}
Write-Host ""

Write-Host "4. 查看login键的详细数据..." -ForegroundColor Yellow
$loginKeys = & $redisCliPath -h 127.0.0.1 -p 6379 keys "login:*"
if ($loginKeys) {
    foreach ($key in $loginKeys) {
        Write-Host ""
        Write-Host "   键名: $key" -ForegroundColor Cyan
        
        # 获取键的类型
        $type = & $redisCliPath -h 127.0.0.1 -p 6379 type $key
        Write-Host "   类型: $type" -ForegroundColor Gray
        
        # 获取TTL（过期时间）
        $ttl = & $redisCliPath -h 127.0.0.1 -p 6379 ttl $key
        if ($ttl -eq "-1") {
            Write-Host "   过期: 永不过期" -ForegroundColor Gray
        } elseif ($ttl -eq "-2") {
            Write-Host "   过期: 键不存在或已过期" -ForegroundColor Red
        } else {
            Write-Host "   过期: $ttl 秒后" -ForegroundColor Gray
        }
        
        # 获取值
        Write-Host "   值:" -ForegroundColor Gray
        $value = & $redisCliPath -h 127.0.0.1 -p 6379 get $key
        if ($value.Length -gt 500) {
            Write-Host "   $($value.Substring(0, 500))..." -ForegroundColor White
            Write-Host "   (内容太长，已截断，完整长度: $($value.Length) 字符)" -ForegroundColor Gray
        } else {
            Write-Host "   $value" -ForegroundColor White
        }
        Write-Host ""
    }
} else {
    Write-Host "   没有login相关的键" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "提示: 如需查看特定键的值，使用命令:" -ForegroundColor Gray
Write-Host "redis-cli.exe -h 127.0.0.1 -p 6379 get 'login:1'" -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Cyan

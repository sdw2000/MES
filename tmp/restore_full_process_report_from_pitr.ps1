param(
  [Parameter(Mandatory=$true)][string]$PitrHost,
  [Parameter(Mandatory=$true)][string]$PitrUser,
  [Parameter(Mandatory=$true)][string]$PitrPassword,
  [string]$PitrDb = 'erp',
  [Parameter(Mandatory=$true)][string]$ProdHost,
  [Parameter(Mandatory=$true)][string]$ProdUser,
  [Parameter(Mandatory=$true)][string]$ProdPassword,
  [string]$ProdDb = 'erp'
)

$ErrorActionPreference = 'Stop'

$nowTag = Get-Date -Format 'yyyyMMdd_HHmmss'
$tmpDir = Join-Path $PSScriptRoot "restore_tmp_$nowTag"
New-Item -Path $tmpDir -ItemType Directory -Force | Out-Null

$dumpFile = Join-Path $tmpDir 'manual_schedule_process_report_full.sql'
$prodBackupTable = "manual_schedule_process_report_backup_$nowTag"
$verifyFile = Join-Path $tmpDir 'verify.txt'

Write-Host "[1/6] 从 PITR 实例导出完整报工历史..." -ForegroundColor Cyan
$env:MYSQL_PWD = $PitrPassword
& mysqldump -h $PitrHost -u $PitrUser $PitrDb manual_schedule_process_report --single-transaction --skip-lock-tables --set-gtid-purged=OFF --default-character-set=utf8mb4 > $dumpFile
Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue

Write-Host "[2/6] 备份生产当前报工表..." -ForegroundColor Cyan
$env:MYSQL_PWD = $ProdPassword
& mysql -h $ProdHost -u $ProdUser -D $ProdDb -e "CREATE TABLE IF NOT EXISTS $prodBackupTable AS SELECT * FROM manual_schedule_process_report;"
Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue

Write-Host "[3/6] 清空生产报工表（仅表数据）..." -ForegroundColor Yellow
$env:MYSQL_PWD = $ProdPassword
& mysql -h $ProdHost -u $ProdUser -D $ProdDb -e "DELETE FROM manual_schedule_process_report;"
Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue

Write-Host "[4/6] 回灌 PITR 完整历史到生产..." -ForegroundColor Cyan
$env:MYSQL_PWD = $ProdPassword
Get-Content -Path $dumpFile -Encoding UTF8 | & mysql -h $ProdHost -u $ProdUser -D $ProdDb
Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue

Write-Host "[5/6] 回灌后核验..." -ForegroundColor Cyan
$env:MYSQL_PWD = $ProdPassword
& mysql -N -B -h $ProdHost -u $ProdUser -D $ProdDb -e "SELECT 'rows',COUNT(*) FROM manual_schedule_process_report; SELECT 'min_id',IFNULL(MIN(id),0) FROM manual_schedule_process_report; SELECT 'max_id',IFNULL(MAX(id),0) FROM manual_schedule_process_report; SELECT DATE(COALESCE(end_time,start_time,created_at)) d, COUNT(*) cnt FROM manual_schedule_process_report WHERE is_deleted=0 GROUP BY DATE(COALESCE(end_time,start_time,created_at)) ORDER BY d DESC LIMIT 10;" | Out-File -FilePath $verifyFile -Encoding UTF8
Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue

Write-Host "[6/6] 完成。" -ForegroundColor Green
Write-Host "备份表: $prodBackupTable" -ForegroundColor Gray
Write-Host "导出文件: $dumpFile" -ForegroundColor Gray
Write-Host "核验结果: $verifyFile" -ForegroundColor Gray

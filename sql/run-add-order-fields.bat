@echo off
chcp 65001 >nul
echo 正在为 schedule_coating 表添加订单字段...
echo.

mysql -h ssdw8127.mysql.rds.aliyuncs.com -P 3306 -u ssdw8127 -pHuateng123 erp < add-order-fields-to-coating.sql

if %errorlevel% equ 0 (
    echo.
    echo ✓ SQL脚本执行成功！
) else (
    echo.
    echo ✗ SQL脚本执行失败！错误代码: %errorlevel%
)

pause

@echo off
chcp 65001 >nul
echo ===================================
echo 客户管理数据库表修复脚本
echo ===================================
echo.

echo 正在创建 customer_code_sequence 表...
echo.

REM 尝试使用常见的MySQL路径
set MYSQL_PATH="C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
if not exist %MYSQL_PATH% (
    set MYSQL_PATH="C:\Program Files\MySQL\MySQL Server 5.7\bin\mysql.exe"
)
if not exist %MYSQL_PATH% (
    set MYSQL_PATH="C:\xampp\mysql\bin\mysql.exe"
)
if not exist %MYSQL_PATH% (
    set MYSQL_PATH=mysql.exe
)

echo 使用MySQL路径: %MYSQL_PATH%
echo.

%MYSQL_PATH% -u root -p123456 mes < fix-customer-simple.sql

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ===================================
    echo ✓ 表创建成功！
    echo ===================================
    echo.
    echo 验证表结构...
    %MYSQL_PATH% -u root -p123456 mes -e "SHOW TABLES LIKE 'customer%%';"
    echo.
    echo 请重启后端服务，然后重试创建客户
) else (
    echo.
    echo ===================================
    echo ✗ 自动创建失败
    echo ===================================
    echo.
    echo 请手动执行以下步骤：
    echo 1. 打开MySQL客户端或管理工具（如Navicat）
    echo 2. 连接到 mes 数据库
    echo 3. 执行 fix-customer-simple.sql 文件
    echo.
)

echo.
pause

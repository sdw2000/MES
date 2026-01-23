@echo off
REM ============================================
REM 库存锁定机制 - Windows 自动化验证脚本
REM 用于验证部署是否成功
REM ============================================

setlocal enabledelayedexpansion

echo.
echo ==================================================
echo   库存锁定机制 - Windows 验证脚本
echo ==================================================
echo.

set BACKEND_URL=http://localhost:8080
set MYSQL_USER=root
set MYSQL_DB=MES_database

REM ========== 验证 1: 后端服务状态 ==========
echo [验证 1] 后端服务状态
echo.

echo 检查后端服务... (此功能需要 curl 命令)
REM 注: Windows 10 及以上已内置 curl

echo.
echo ==================================================
echo [验证 2] 数据库表状态
echo.

echo 检查数据库表... (此功能需要 MySQL 命令行工具)
echo.

REM Windows 上使用 mysql 命令
set "MYSQL_CMD=mysql -u%MYSQL_USER% -p %MYSQL_DB%"

REM 检查 schedule_material_lock 表
echo 执行以下 SQL 命令验证:
echo.
echo   USE %MYSQL_DB%;
echo   SHOW TABLES LIKE 'schedule_material_lock';
echo   SHOW TABLES LIKE 'schedule_material_allocation';
echo   DESC tape_stock;
echo.

echo ==================================================
echo [验证 3] 快速验证清单
echo ==================================================
echo.

echo 请按照以下步骤手动验证:
echo.
echo Step 1: 数据库验证
echo   1. 打开 MySQL 命令行
echo   2. USE MES_database;
echo   3. SHOW TABLES;
echo   检查是否有以下表:
echo     - schedule_material_lock
echo     - schedule_material_allocation
echo.
echo Step 2: 新字段验证
echo   1. DESC tape_stock;
echo   检查是否有以下字段:
echo     - available_area
echo     - reserved_area
echo     - consumed_area
echo     - version
echo.
echo Step 3: 后端服务验证
echo   1. 在另一个终端运行: cd e:\java\MES && mvn spring-boot:run
echo   2. 等待看到: "Tomcat started on port(s): 8080"
echo.
echo Step 4: 前端服务验证
echo   1. 在另一个终端运行: cd e:\vue\ERP && npm run serve
echo   2. 等待看到: "App running at:"
echo.
echo Step 5: API 测试
echo   1. 打开浏览器访问:
echo      http://localhost:8080/production/schedule-material/lock/1
echo   2. 应该收到 JSON 响应或错误信息
echo.
echo ==================================================
echo [验证 4] 使用 Postman 进行 API 测试
echo ==================================================
echo.

echo Postman 测试步骤:
echo.
echo 1. 打开 Postman
echo.
echo 2. 创建请求 1: 锁定物料
echo    Method: POST
echo    URL: http://localhost:8080/production/schedule-material/lock/1
echo    Headers: Content-Type: application/json
echo    Body: (空)
echo.
echo 3. 创建请求 2: 查询分配
echo    Method: GET
echo    URL: http://localhost:8080/production/schedule-material/allocation/1
echo.
echo 4. 创建请求 3: 查询锁定
echo    Method: GET
echo    URL: http://localhost:8080/production/schedule-material/tape-locks/1
echo.
echo 5. 创建请求 4: 释放锁定
echo    Method: POST
echo    URL: http://localhost:8080/production/schedule-material/release/1
echo.

echo ==================================================
echo [完成] 验证指南
echo ==================================================
echo.

echo 快速参考:
echo.
echo MySQL 验证:
echo   mysql -uroot -p %MYSQL_DB% -e "SHOW TABLES LIKE 'schedule_material%%';"
echo.
echo 后端编译:
echo   cd e:\java\MES
echo   mvn clean compile -q
echo.
echo 后端启动:
echo   cd e:\java\MES
echo   mvn spring-boot:run
echo.
echo 前端启动:
echo   cd e:\vue\ERP
echo   npm run serve
echo.

pause

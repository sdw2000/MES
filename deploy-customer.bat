@echo off
echo ========================================
echo   客户管理功能 - 数据库部署
echo ========================================
echo.

echo 步骤 1/3: 创建表结构
mysql -uroot -p erp < create-customer-tables.sql
if %errorlevel% neq 0 (
    echo 失败：表结构创建失败
    pause
    exit /b 1
)
echo 成功：表结构创建完成
echo.

echo 步骤 2/3: 插入测试数据
mysql -uroot -p erp < insert-customer-test-data.sql
if %errorlevel% neq 0 (
    echo 失败：测试数据插入失败
    pause
    exit /b 1
)
echo 成功：测试数据插入完成
echo.

echo 步骤 3/3: 验证数据
mysql -uroot -p erp -e "SELECT COUNT(*) as customer_count FROM customers WHERE is_deleted = 0; SELECT COUNT(*) as contact_count FROM customer_contacts;"
echo.

echo ========================================
echo   部署完成！
echo ========================================
echo.
echo 已创建的表：
echo - customers (客户主表)
echo - customer_contacts (联系人表)
echo - customer_code_sequence (编号序列表)
echo.
echo 测试数据：
echo - 7个客户
echo - 14个联系人
echo.
echo 下一步：
echo 1. 启动后端：start-backend.bat
echo 2. 启动前端：cd e:\vue\ERP 然后 npm run dev
echo 3. 访问：http://localhost:8080/#/sales/customers
echo.
pause

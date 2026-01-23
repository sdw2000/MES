@echo off
echo ========================================
echo   启动送样功能后端服务
echo ========================================
echo.

cd /d e:\java\MES

echo 步骤1: 清理旧的编译文件...
call mvn clean

echo.
echo 步骤2: 编译项目...
call mvn compile -DskipTests

echo.
echo 步骤3: 打包项目...
call mvn package -DskipTests

echo.
echo 步骤4: 启动服务...
echo 服务将运行在端口: 8090
echo 按 Ctrl+C 停止服务
echo.

java -jar target\MES-0.0.1-SNAPSHOT.jar

pause

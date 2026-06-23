@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

title 智慧在线考试系统 - 一键启动

cd /d "%~dp0\.."
set "ROOT=%cd%"

echo ============================================================
echo 智慧在线考试系统 - 一键启动
echo ============================================================
echo.
echo 项目目录：%ROOT%
echo.

echo [1/4] 检查 Java...
java -version >nul 2>&1
if errorlevel 1 (
    echo 未检测到 Java。请安装 JDK 17 或更高版本。
    pause
    exit /b 1
)

echo [2/4] 查找后端 Jar...
set "JAR="
for /f "delims=" %%f in ('dir /b /s "%ROOT%\backend\target\smart-exam-backend-*.jar" 2^>nul') do (
    if not defined JAR set "JAR=%%f"
)

if not defined JAR (
    echo 未找到后端 Jar。请先运行：
    echo   cd backend
    echo   mvn clean package -DskipTests
    pause
    exit /b 1
)

echo 找到 Jar：!JAR!
echo.

echo [3/4] 数据库准备提示
echo 首次运行请先在 MySQL 中创建数据库：
echo   CREATE DATABASE IF NOT EXISTS smart_exam_system CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
echo 后端启动后会自动执行 schema.sql 和 data.sql。
echo.

echo [4/4] 启动后端并打开系统...
echo 默认地址：http://localhost:8080
echo 如需修改数据库账号密码，请参考 docs\deploy-local.md。
echo.

start "" cmd /c "timeout /t 12 >nul && start http://localhost:8080"
java -jar "!JAR!"

echo.
echo 后端已停止。
pause
endlocal

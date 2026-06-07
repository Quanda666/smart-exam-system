@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

REM ============================================================
REM 智慧在线考试系统 - Windows 一键启动脚本
REM 双击运行即可启动后端，并自动打开前端页面
REM ============================================================

title 智慧在线考试系统 - 启动器
echo.
echo ============================================================
echo            智慧在线考试系统 - 一键启动
echo ============================================================
echo.

REM ---------- 定位项目根目录（本脚本位于 docs\ 下） ----------
cd /d "%~dp0\.."
set "ROOT=%cd%"
echo [信息] 项目根目录：%ROOT%
echo.

REM ---------- 1. 检测 JDK ----------
echo [1/4] 检测 Java 运行环境...
java -version >nul 2>&1
if errorlevel 1 (
    echo [错误] 未检测到 Java，请先安装 JDK 17 或更高版本。
    echo        下载地址：https://adoptium.net/
    echo.
    pause
    exit /b 1
)
echo       Java 环境正常。
echo.

REM ---------- 2. 定位后端 jar ----------
echo [2/4] 查找后端 jar 包...
set "JAR="
for %%f in ("%ROOT%\backend\target\smart-exam-backend-*.jar") do set "JAR=%%f"
if not defined JAR (
    echo [错误] 未找到后端 jar 包，请先在 backend 目录执行：mvn clean package -DskipTests
    echo.
    pause
    exit /b 1
)
echo       找到：!JAR!
echo.

REM ---------- 3. 提示数据库初始化 ----------
echo [3/4] 数据库准备提示
echo       如果是首次运行，请先在命令行执行（仅需一次）：
echo           mysql -u root -p ^< docs\init.sql
echo       默认数据库连接：localhost:3306 / 用户 root / 密码 root
echo       如与你的 MySQL 不一致，请按 docs\deploy-local.md 的「方式 3」修改。
echo.

REM ---------- 4. 启动后端 ----------
echo [4/4] 正在启动后端（端口 8080）...
echo       启动成功后会自动打开前端页面，关闭本窗口即停止后端。
echo.

REM 延迟 12 秒后打开前端（等待后端起来）
start "" cmd /c "timeout /t 12 >nul & start "" "%ROOT%\frontend\dist\index.html""

REM 前台运行后端，日志直接显示在本窗口
java -jar "!JAR!"

echo.
echo [信息] 后端已停止。
pause
endlocal

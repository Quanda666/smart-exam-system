@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

title 智慧在线考试系统 - 一键启动（交付验收版）

:: =====================================================================
:: 交付给老师的一键启动脚本
:: 前提：老师已安装 Docker Desktop 并启动（右下角鲸鱼变绿）
:: 功能：自动构建并拉起 MySQL + Redis + 一体化应用，浏览器自动打开
:: =====================================================================

cd /d "%~dp0"

echo ============================================================
echo   智慧在线考试系统 - 一键启动（交付验收版）
echo ============================================================
echo.
echo   当前目录：%cd%
echo.

:: 1. 检查 Docker
echo [1/5] 检查 Docker 环境...
docker info >nul 2>&1
if errorlevel 1 (
    echo   [错误] 未检测到 Docker 或 Docker Desktop 未启动。
    echo   请先安装并启动 Docker Desktop：
    echo     https://www.docker.com/products/docker-desktop/
    echo   等待右下角鲸鱼图标变成绿色后，重新运行本脚本。
    pause
    exit /b 1
)
echo   Docker 就绪。
echo.

:: 2. 检查必要文件
echo [2/5] 检查交付文件...
set "MISSING=0"
if not exist "docker-compose.teacher.yml" (
    echo   [缺少] docker-compose.teacher.yml
    set "MISSING=1"
)
if not exist "Dockerfile" (
    echo   [缺少] Dockerfile（一体化构建文件）
    set "MISSING=1"
)
if not exist "backend\src\main\resources\db\schema.sql" (
    echo   [缺少] backend\src\main\resources\db\schema.sql
    set "MISSING=1"
)
if not exist "backend\src\main\resources\db\data.sql" (
    echo   [缺少] backend\src\main\resources\db\data.sql
    set "MISSING=1"
)
if "!MISSING!"=="1" (
    echo   请确保所有交付文件与脚本放在同一目录下。
    pause
    exit /b 1
)
echo   交付文件完整。
echo.

:: 3. 构建并启动服务
echo [3/5] 构建并启动服务...
echo   首次启动需要 5-10 分钟编译项目（后续启动只需数秒）。
echo   请耐心等待，不要关闭此窗口...
echo.

docker-compose -f docker-compose.teacher.yml up -d --build
if errorlevel 1 (
    echo   [错误] 服务启动失败，请查看上方错误信息。
    pause
    exit /b 1
)
echo   容器已创建，等待系统就绪...
echo.

:: 4. 等待健康检查通过
echo [4/5] 等待系统就绪（最多等待 3 分钟）...

set "READY=0"
for /l %%i in (1,1,36) do (
    curl -s -o nul -w "%%{http_code}" http://localhost:8080/api/health 2>nul | findstr "200" >nul
    if not errorlevel 1 (
        set "READY=1"
        goto :ready
    )
    :: 每 5 秒检查一次
    timeout /t 5 /nobreak >nul
    echo   ...%%i/36
)

:ready
if "!READY!"=="0" (
    echo.
    echo   [注意] 超时未检测到系统就绪，可能仍在构建中。
    echo   请查看 Docker Desktop 中 smart-exam-app 容器的日志。
    echo   构建完成后手动打开 http://localhost:8080
    echo.
) else (
    echo   系统已就绪！
    echo.
)

:: 5. 打开浏览器
echo [5/5] 正在打开浏览器...

start "" http://localhost:8080

echo ============================================================
echo   启动完成！
echo.
echo   ??  访问地址：http://localhost:8080
echo   ??  管理员账号：admin
echo   ??  管理员密码：admin123
echo.
echo   登录后建议立即修改管理员密码！
echo.
echo ============================================================
echo   常用操作：
echo.
echo   停止系统（数据保留）：  docker-compose -f docker-compose.teacher.yml down
echo   清空数据重新开始：      docker-compose -f docker-compose.teacher.yml down -v
echo   重新启动：              再次双击本脚本
echo ============================================================
echo.
echo   你可以关闭此窗口，系统在 Docker 后台继续运行。

pause
endlocal

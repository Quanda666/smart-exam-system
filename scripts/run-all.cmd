@echo off
:: =====================================================================
:: Smart Exam System 一键极速本地构建与启动脚本 (托管模式)
:: =====================================================================
setlocal EnabledDelayedExpansion

echo ===================================================
echo [Smart Exam System] 正在准备本地一键构建并托管启动...
echo ===================================================
echo.

:: 1. 查找并确定本地 Maven 路径
set "MAVEN_CMD="
where mvn >nul 2>nul
if %errorlevel%==0 (
  for /f "tokens=*" %%i in ('where mvn') do (
    if not defined MAVEN_CMD set "MAVEN_CMD=%%i"
  )
)

if not defined MAVEN_CMD if defined MAVEN_HOME if exist "%MAVEN_HOME%\bin\mvn.cmd" set "MAVEN_CMD=%MAVEN_HOME%\bin\mvn.cmd"
if not defined MAVEN_CMD if defined M2_HOME if exist "%M2_HOME%\bin\mvn.cmd" set "MAVEN_CMD=%M2_HOME%\bin\mvn.cmd"
if not defined MAVEN_CMD if exist "%USERPROFILE%\.local\apache-maven-3.9.16\bin\mvn.cmd" set "MAVEN_CMD=%USERPROFILE%\.local\apache-maven-3.9.16\bin\mvn.cmd"
if not defined MAVEN_CMD if exist "%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.6-bin\439sdfsg2nbdob9ciift5h5nse\apache-maven-3.9.6\bin\mvn.cmd" set "MAVEN_CMD=%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.6-bin\439sdfsg2nbdob9ciift5h5nse\apache-maven-3.9.6\bin\mvn.cmd"
if not defined MAVEN_CMD if exist "%APPDATA%\JetBrains\IntelliJIdea2025.3\plugins\maven\lib\maven3\bin\mvn.cmd" set "MAVEN_CMD=%APPDATA%\JetBrains\IntelliJIdea2025.3\plugins\maven\lib\maven3\bin\mvn.cmd"

if not defined MAVEN_CMD (
  echo [错误] 未能找到 Maven 路径。
  echo 请确保已安装 Maven，或者在 IDE (如 IntelliJ IDEA) 中启动本项目。
  pause
  exit /b 1
)

:: 2. 检查 Java
where java >nul 2>nul
if %errorlevel% neq 0 (
  echo [错误] 未能找到 Java 环境。请安装 JDK 17+。
  pause
  exit /b 1
)

:: 3. 构建前端并打包静态资源
echo [Step 1/3] 正在构建前端静态资源...
cd /d "%~dp0\..\frontend"

if not exist "node_modules" (
  echo 正在安装前端依赖 (npm install)...
  call npm install
  if !errorlevel! neq 0 (
    echo [错误] 前端依赖安装失败！
    pause
    exit /b 1
  )
)

echo 正在编译构建前端生产包 (npm run build)...
call npm run build
if !errorlevel! neq 0 (
  echo [错误] 前端打包编译失败！
  pause
  exit /b 1
)

:: 4. 清理并同步前端构建产物到 Spring Boot 的静态资源目录
echo [Step 2/3] 正在同步构建产物到后端托管目录...
set "STATIC_DIR=%~dp0\..\backend\src\main\resources\static"

if exist "%STATIC_DIR%" (
  rd /s /q "%STATIC_DIR%"
)
mkdir "%STATIC_DIR%"

xcopy /s /e /y "dist\*" "%STATIC_DIR%\" >nul
if !errorlevel! neq 0 (
  echo [错误] 前端构建产物同步到后端目录失败！
  pause
  exit /b 1
)

:: 5. 编译并启动后端 (在托管模式下运行整个应用)
echo [Step 3/3] 正在通过 Maven 编译、启动后端服务 (托管前端)...
cd /d "%~dp0\..\backend"

echo.
echo ===================================================
echo [启动成功预告]
echo 系统跑起后，你不需要启动任何前端开发服务！
echo 请直接用浏览器访问以下一揽子服务地址：
echo.
echo >> 统一入口 (含有完整前端与API)： http://localhost:8080
echo >> 系统健康检测接口：              http://localhost:8080/api/health
echo ===================================================
echo.

call "%MAVEN_CMD%" spring-boot:run

endlocal

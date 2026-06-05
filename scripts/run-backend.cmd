@echo off
setlocal

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
  echo Maven was not found in PATH or known local locations.
  echo Please install Maven 3.8+ or set MAVEN_HOME, then run this script again.
  exit /b 1
)

cd /d %~dp0\..\backend
echo Starting backend service with Maven: %MAVEN_CMD%
call "%MAVEN_CMD%" spring-boot:run
endlocal

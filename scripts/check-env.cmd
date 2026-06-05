@echo off
setlocal

echo [Smart Exam System] Environment check
echo.

where java >nul 2>nul
if %errorlevel%==0 (
  for /f "tokens=*" %%i in ('where java') do echo Java: %%i
) else (
  echo Java: NOT FOUND
)

where javac >nul 2>nul
if %errorlevel%==0 (
  for /f "tokens=*" %%i in ('where javac') do echo Javac: %%i
) else (
  echo Javac: NOT FOUND
)

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

if defined MAVEN_CMD (
  echo Maven: %MAVEN_CMD%
  call "%MAVEN_CMD%" -v
) else (
  echo Maven: NOT FOUND. Please install Maven 3.8+ or configure it in PATH before running backend.
)

where node >nul 2>nul
if %errorlevel%==0 (
  for /f "tokens=*" %%i in ('where node') do echo Node: %%i
) else (
  echo Node: NOT FOUND
)

where npm >nul 2>nul
if %errorlevel%==0 (
  for /f "tokens=*" %%i in ('where npm') do echo npm: %%i
) else (
  echo npm: NOT FOUND
)

echo.
if exist frontend\package-lock.json (
  echo Frontend lock file: FOUND
) else (
  echo Frontend lock file: NOT FOUND. Run scripts\run-frontend.cmd first.
)

if exist frontend\node_modules (
  echo Frontend node_modules: FOUND
) else (
  echo Frontend node_modules: NOT FOUND. Run scripts\run-frontend.cmd to install dependencies.
)

echo.
echo Done.
endlocal

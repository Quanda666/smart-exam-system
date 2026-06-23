@echo off
setlocal
cd /d %~dp0\..\frontend

if not exist node_modules (
  echo Installing frontend dependencies...
  call npm install
  if errorlevel 1 exit /b 1
)

echo Starting frontend dev server...
call npm run dev -- --host 127.0.0.1 --port 3000
endlocal

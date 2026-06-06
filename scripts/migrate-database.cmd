@echo off
REM Database Migration Script for Smart Exam System (Windows)
REM This script helps initialize or migrate your MySQL database

echo ========================================
echo Smart Exam System - Database Migration
echo ========================================
echo.

REM Check if .env file exists
if exist .env (
    echo [OK] Loading configuration from .env file
    for /f "tokens=*" %%a in ('type .env ^| findstr /v "^#"') do set %%a
) else (
    echo [WARNING] No .env file found, using default values
)

REM Check required variables
if "%MYSQL_URL%"=="" (
    echo [ERROR] MYSQL_URL not set. Please set it in .env file
    exit /b 1
)

REM Parse database details (simplified for Windows)
REM For complex parsing, you may need to set these manually in .env:
REM DB_HOST, DB_PORT, DB_NAME, MYSQL_USERNAME, MYSQL_PASSWORD

if "%DB_HOST%"=="" set DB_HOST=localhost
if "%DB_PORT%"=="" set DB_PORT=3306
if "%DB_NAME%"=="" set DB_NAME=smart_exam_system
if "%MYSQL_USERNAME%"=="" set MYSQL_USERNAME=root
if "%MYSQL_PASSWORD%"=="" set MYSQL_PASSWORD=root

echo Database Configuration:
echo   Host: %DB_HOST%
echo   Port: %DB_PORT%
echo   Database: %DB_NAME%
echo   User: %MYSQL_USERNAME%
echo.

REM Check if MySQL client is available
where mysql >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] MySQL client not found. Please install MySQL client first.
    echo   Download from https://dev.mysql.com/downloads/mysql/
    exit /b 1
)

REM Test database connection
echo Testing database connection...
mysql -h%DB_HOST% -P%DB_PORT% -u%MYSQL_USERNAME% -p%MYSQL_PASSWORD% -e "SELECT 1" >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Failed to connect to database
    echo   Please check your database credentials
    exit /b 1
)
echo [OK] Database connection successful
echo.

REM Check if database exists
echo Checking if database exists...
mysql -h%DB_HOST% -P%DB_PORT% -u%MYSQL_USERNAME% -p%MYSQL_PASSWORD% -e "USE %DB_NAME%" >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo [WARNING] Database '%DB_NAME%' does not exist. Creating...
    mysql -h%DB_HOST% -P%DB_PORT% -u%MYSQL_USERNAME% -p%MYSQL_PASSWORD% -e "CREATE DATABASE IF NOT EXISTS %DB_NAME% CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
    echo [OK] Database '%DB_NAME%' created
) else (
    echo [OK] Database '%DB_NAME%' exists
)
echo.

REM Run schema migration
echo Running schema migration...
if exist backend\src\main\resources\db\schema.sql (
    mysql -h%DB_HOST% -P%DB_PORT% -u%MYSQL_USERNAME% -p%MYSQL_PASSWORD% %DB_NAME% < backend\src\main\resources\db\schema.sql
    echo [OK] Schema migration completed
) else (
    echo [ERROR] Schema file not found: backend\src\main\resources\db\schema.sql
    exit /b 1
)
echo.

REM Run data seeding
echo Running data seeding...
if exist backend\src\main\resources\db\data.sql (
    mysql -h%DB_HOST% -P%DB_PORT% -u%MYSQL_USERNAME% -p%MYSQL_PASSWORD% %DB_NAME% < backend\src\main\resources\db\data.sql
    echo [OK] Data seeding completed
) else (
    echo [WARNING] Data file not found: backend\src\main\resources\db\data.sql
)
echo.

REM Show tables
echo Database tables:
mysql -h%DB_HOST% -P%DB_PORT% -u%MYSQL_USERNAME% -p%MYSQL_PASSWORD% %DB_NAME% -e "SHOW TABLES;"
echo.

echo ========================================
echo [OK] Database migration completed successfully!
echo ========================================
echo.
echo Default admin account:
echo   Username: admin
echo   Password: admin123
echo.
echo Important: Please change the admin password after first login!
echo.
pause

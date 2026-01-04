@echo off
cd /d %~dp0

echo Checking Node.js...
node -v
if %ERRORLEVEL% neq 0 (
    echo ERROR: Node.js is not installed or not in PATH
    pause
    exit /b 1
)

echo.
echo Starting Frontend Server...
npm run dev
pause

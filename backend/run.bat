@echo off
cd /d %~dp0

REM ============================================================
REM 数据库配置 (选择其一，注释另一个)
REM ============================================================

REM 方式1: 使用本地 PostgreSQL (推荐)
REM 请确认你的用户名和密码，PostgreSQL默认用户通常是 postgres
set DATABASE_URL=postgresql://postgres:123456@localhost:5432/paperless_meeting

REM 方式2: 使用 SQLite (无需额外配置)
REM set DATABASE_URL=

REM ============================================================

echo Installing dependencies...
pip install -r requirements.txt

echo.
echo Database: %DATABASE_URL%
echo.
echo Starting Backend Server...
python -m uvicorn main:app --host 0.0.0.0 --port 8001 --reload
pause

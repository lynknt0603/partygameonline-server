@echo off
chcp 65001 >nul
echo ===================================================
echo   Đang khởi động PostgreSQL 16 (Portable)...
echo ===================================================
"%USERPROFILE%\tools\pgsql\bin\pg_ctl.exe" -D "%USERPROFILE%\tools\pgsql\data" -l "%USERPROFILE%\tools\pgsql\logfile.log" start
echo.
echo [✓] PostgreSQL đã sẵn sàng tại localhost:5432!
echo     - Database dev:  partygameonline
echo     - Database test: partygameonline_test
echo     - User / Pass:   partygameonline / partygameonline
echo.
timeout /t 3 >nul

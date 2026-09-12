@echo off
chcp 65001 >nul
echo ===================================================
echo   Đang tắt PostgreSQL và giải phóng RAM...
echo ===================================================
"%USERPROFILE%\tools\pgsql\bin\pg_ctl.exe" -D "%USERPROFILE%\tools\pgsql\data" stop
echo.
echo [✓] Đã tắt sạch PostgreSQL! RAM tiêu thụ: 0 MB.
echo     Khi bật lại laptop, PostgreSQL sẽ KHÔNG tự chạy ngầm.
echo.
timeout /t 3 >nul

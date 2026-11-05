@echo off
REM ============================================================
REM  ArtistikCity - CLEAR ALL SITE DATA, then start the site
REM  Deletes every student, course, workshop, free lesson, order,
REM  coursework upload, certificate, painting, print order, coupon,
REM  commission, message, wish list, blog post, testimonial, home
REM  gallery picture, contact message and newsletter subscriber.
REM  Keeps: staff logins, site settings, FAQs, mediums / genres /
REM  skills / course types, blog categories and the print price list.
REM  A full database backup is taken first and uploaded files are
REM  moved (not deleted) into backups\wipe-<date>\. You must type
REM  WIPE to confirm. Restore: mysql artistikcity < that backup .sql
REM ============================================================
REM ---- keep this window open whatever happens (re-runs itself inside "cmd /k") ----
if not defined ARTISTIK_KEEP_OPEN (
  set ARTISTIK_KEEP_OPEN=1
  cmd /k call "%~f0" %*
  exit /b
)
cd /d "%~dp0"
if not exist "logs" mkdir "logs"
echo [%date% %time%] %~nx0 started in "%CD%" with args: %* > "logs\launcher.log"
where powershell >> "logs\launcher.log" 2>&1
if errorlevel 1 (
  echo ERROR: Windows PowerShell was not found on this computer.
  echo ERROR: powershell not found >> "logs\launcher.log"
  goto :eof
)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup-and-run.ps1" -WipeData %*
echo [%date% %time%] setup-and-run.ps1 exited with code %errorlevel% >> "logs\launcher.log"
echo.
echo ---------------------------------------------------------------
echo  Finished. Logs: "%CD%\logs"  (setup.log, app.log, launcher.log)
echo  You can close this window.
echo ---------------------------------------------------------------

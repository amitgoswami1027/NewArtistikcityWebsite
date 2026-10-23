@echo off
REM ============================================================
REM  ArtistikCity - start only the MySQL database (portable, in .tools\)
REM  Use this before running the app from VS Code (F5).
REM  MySQL keeps running in the background until STOP-MySQL.bat.
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
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup-and-run.ps1" -DatabaseOnly %*
echo [%date% %time%] setup-and-run.ps1 exited with code %errorlevel% >> "logs\launcher.log"
echo.
echo ---------------------------------------------------------------
echo  Finished. Logs: "%CD%\logs"  (setup.log, app.log, launcher.log)
echo  You can close this window.
echo ---------------------------------------------------------------

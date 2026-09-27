@echo off
REM ============================================================
REM  ArtistikCity - start against your own Microsoft SQL Server
REM  Default: localhost:1433 with Windows login. Examples:
REM    START-with-SQLServer.bat -SqlUser sa -SqlPassword "YourPass"
REM    START-with-SQLServer.bat -SqlInstance SQLEXPRESS -Trusted
REM    START-with-SQLServer.bat -Trusted -ResetDatabase
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
if "%~1"=="" (
  powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup-and-run.ps1" -Db sqlserver -Trusted
) else (
  powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup-and-run.ps1" -Db sqlserver %*
)
echo [%date% %time%] setup-and-run.ps1 exited with code %errorlevel% >> "logs\launcher.log"
echo.
echo ---------------------------------------------------------------
echo  Finished. Logs: "%CD%\logs"  (setup.log, app.log, launcher.log)
echo  You can close this window.
echo ---------------------------------------------------------------

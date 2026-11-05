@echo off
REM ============================================================
REM  ArtistikCity - PUT BACK the data cleared by WIPE-SITE-DATA.bat
REM  Loads the newest backups\wipe-<date>\artistikcity.sql and moves
REM  the uploaded pictures back into storage, then starts the site.
REM  Everything in the database now is replaced by that backup; a copy
REM  of the current data is saved to backups\before-restore-<date>\ first.
REM  You must type RESTORE to confirm.
REM  Another backup:  RESTORE-SITE-DATA.bat -RestoreFrom backups\wipe-<date>
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
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup-and-run.ps1" -RestoreData %*
echo [%date% %time%] setup-and-run.ps1 exited with code %errorlevel% >> "logs\launcher.log"
echo.
echo ---------------------------------------------------------------
echo  Finished. Logs: "%CD%\logs"  (setup.log, app.log, launcher.log)
echo  You can close this window.
echo ---------------------------------------------------------------

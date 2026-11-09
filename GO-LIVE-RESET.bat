@echo off
REM ============================================================
REM  ArtistikCity - GO-LIVE RESET of the LIVE (AWS) website
REM  1 super admin, 1 student, 6 sample courses; configuration kept; final RDS snapshot first,
REM  then older snapshots and backups deleted. Run with -DryRun first. See GO-LIVE-RESET.ps1 / docs/GO-LIVE.md.
REM ============================================================
if not defined ARTISTIK_KEEP_OPEN (
  set ARTISTIK_KEEP_OPEN=1
  cmd /k call "%~f0" %*
  exit /b
)
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0GO-LIVE-RESET.ps1" %*

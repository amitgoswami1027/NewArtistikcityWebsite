@echo off
REM ArtistikCity - bcrypt hash of a password for the go-live secrets (see HASH-PASSWORD.ps1, docs/GO-LIVE.md)
if not defined ARTISTIK_KEEP_OPEN (
  set ARTISTIK_KEEP_OPEN=1
  cmd /k call "%~f0" %*
  exit /b
)
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0HASH-PASSWORD.ps1" %*

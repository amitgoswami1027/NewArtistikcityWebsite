@echo off
REM ============================================================
REM  ArtistikCity - trim the LIVE (AWS) website data to a small starter set
REM  Keeps 2 students, 2 courses, 2 paintings, 2 blog posts and 3 staff logins
REM  (lists at the top of database\maintenance\trim-to-starter.sql).
REM  Takes an RDS snapshot first (undo point). You must type TRIM to confirm.
REM ============================================================
if not defined ARTISTIK_KEEP_OPEN (
  set ARTISTIK_KEEP_OPEN=1
  cmd /k call "%~f0" %*
  exit /b
)
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0CLEAN-AWS-DATA.ps1" %*

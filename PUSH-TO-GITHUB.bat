@echo off
REM ============================================================
REM  Commits this folder and pushes it to
REM  https://github.com/amitgoswami1027/Artistikcity  (branch main)
REM  Git asks you to sign in to GitHub in the browser the first time.
REM ============================================================
cd /d "%~dp0"
set REPO=https://github.com/amitgoswami1027/Artistikcity.git
where git >nul 2>nul
if errorlevel 1 (
  echo Git is not installed. Install it from https://git-scm.com/download/win and run this again.
  pause & exit /b 1
)

if not exist ".git" (
  echo ==^> Initialising a git repository in this folder
  git init -q
  git symbolic-ref HEAD refs/heads/main
  git remote add origin %REPO%
)
git config core.autocrlf true
git config user.name >nul 2>nul || git config user.name "Amit Goswami"
git config user.email >nul 2>nul || git config user.email "amitgoswami1027@gmail.com"

echo ==^> Fetching %REPO%
git fetch origin main
if errorlevel 1 (
  echo Could not reach the GitHub repository - check your internet connection and sign-in.
  pause & exit /b 1
)
REM first time: build on top of the existing "Initial commit" so no force-push is needed
git rev-parse --verify -q HEAD >nul 2>nul || git reset -q --soft origin/main

echo ==^> Committing
git add -A
git diff --cached --quiet
if errorlevel 1 (
  git commit -q -m "Convert ArtistikCity to Spring Boot + React + SQL Server" -m "Port of the Laravel 8 + Inertia.js + React app to Java 17 / Spring Boot 3.3 with Microsoft SQL Server, keeping the React UI. Includes T-SQL schema + seed data, one-click Windows setup and Docker (SQL Server 2022 + app)."
) else (
  echo Nothing new to commit.
)

echo ==^> Pushing to GitHub (a browser sign-in window may open)
git push -u origin main
if errorlevel 1 (
  echo.
  echo Push failed. If GitHub says the branch has new commits, run:  git pull --rebase origin main  and then run this file again.
  pause & exit /b 1
)
echo.
echo Done: https://github.com/amitgoswami1027/Artistikcity
pause

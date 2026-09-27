@echo off
REM ============================================================
REM  Commits this folder and pushes it to
REM  https://github.com/amitgoswami1027/NewArtistikcityWebsite  (branch main)
REM  Git may ask you to sign in to GitHub in the browser the first time.
REM ============================================================
cd /d "%~dp0"
set REPO=https://github.com/amitgoswami1027/NewArtistikcityWebsite.git
echo ==== push to NewArtistikcityWebsite ==== > "%~dp0push-github.log"
where git >nul 2>nul
if errorlevel 1 (
  echo GIT_NOT_FOUND>> "%~dp0push-github.log"
  echo Git is not installed. Install it from https://git-scm.com/download/win and run this again.
  pause & exit /b 1
)

if not exist ".git" (
  echo ==^> Initialising a git repository in this folder
  git init -q
  git symbolic-ref HEAD refs/heads/main
)
git remote get-url newsite >nul 2>nul || git remote add newsite %REPO%
git config core.autocrlf true
git config user.name >nul 2>nul || git config user.name "Amit Goswami"
git config user.email >nul 2>nul || git config user.email "amitgoswami1027@gmail.com"

echo ==^> Fetching %REPO%
git fetch newsite main >> "%~dp0push-github.log" 2>&1
if errorlevel 1 (
  echo FETCH_FAILED>> "%~dp0push-github.log"
  echo Could not reach the GitHub repository - check your internet connection and sign-in.
  pause & exit /b 1
)
REM first time: build on top of the repository's existing commit so no force-push is needed
git rev-parse --verify -q HEAD >nul 2>nul || git reset -q --soft newsite/main

echo ==^> Committing
git add -A >> "%~dp0push-github.log" 2>&1
git diff --cached --quiet
if errorlevel 1 (
  git commit -q -F "%~dp0tools\commit-message.txt" >> "%~dp0push-github.log" 2>&1
) else (
  echo Nothing new to commit.
)

echo ==^> Pushing to GitHub (a browser sign-in window may open)
git push -u newsite HEAD:main >> "%~dp0push-github.log" 2>&1
if errorlevel 1 (
  echo PUSH_FAILED>> "%~dp0push-github.log"
  echo.
  echo Push failed - see push-github.log. If GitHub says the branch has new commits, run:  git pull --rebase newsite main  and then run this file again.
  pause & exit /b 1
)
echo PUSH_OK>> "%~dp0push-github.log"
echo.
echo Done: https://github.com/amitgoswami1027/NewArtistikcityWebsite
timeout /t 8

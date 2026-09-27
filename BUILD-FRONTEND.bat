@echo off
rem Rebuilds the React bundle (src\main\resources\static\js\app.js) from frontend\resources
cd /d "%~dp0frontend"
echo ==== ArtistikCity frontend build ==== > "%~dp0build-frontend.log"
where npm >> "%~dp0build-frontend.log" 2>&1
if errorlevel 1 (echo NPM_NOT_FOUND>> "%~dp0build-frontend.log" & echo Node.js / npm is not installed. Install it from https://nodejs.org and run this again. & pause & exit /b 1)
echo Installing packages, please wait...
call npm install --no-audit --no-fund >> "%~dp0build-frontend.log" 2>&1
if errorlevel 1 (echo NPM_INSTALL_FAILED>> "%~dp0build-frontend.log" & echo npm install failed - see build-frontend.log & pause & exit /b 1)
echo Building...
call npm run dev >> "%~dp0build-frontend.log" 2>&1
if errorlevel 1 (echo BUILD_FAILED>> "%~dp0build-frontend.log" & echo Build failed - see build-frontend.log & pause & exit /b 1)
echo BUILD_OK>> "%~dp0build-frontend.log"
echo Build finished OK.
timeout /t 5

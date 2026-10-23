@echo off
REM ============================================================
REM  ArtistikCity - run the whole stack in Docker Desktop:
REM    db       MySQL 8.4 (creates the database + demo data on the first run)
REM    app      Spring Boot backend + React UI on http://localhost:8080
REM  First run downloads ~1 GB of images and builds the app (5-15 min).
REM ============================================================
cd /d "%~dp0"
where docker >nul 2>nul
if errorlevel 1 (
  echo Docker Desktop is not installed. Get it from https://www.docker.com/products/docker-desktop/
  pause & exit /b 1
)
docker info >nul 2>nul
if errorlevel 1 (
  echo Docker Desktop is not running. Start Docker Desktop, wait until it says "Engine running", then run this again.
  pause & exit /b 1
)

echo.
echo ==^> Building and starting containers (db, app)...
docker compose up -d --build
if errorlevel 1 (
  echo.
  echo ERROR: docker compose failed - see the messages above.
  pause & exit /b 1
)

echo.
echo ==^> Waiting for the website to come up...
powershell -NoProfile -Command "for($i=0;$i -lt 100;$i++){try{Invoke-WebRequest 'http://localhost:8080/' -UseBasicParsing -TimeoutSec 5 | Out-Null; exit 0}catch{Start-Sleep -Seconds 3}}; exit 1"
if errorlevel 1 (
  echo.
  echo The app did not answer within 5 minutes. Recent logs:
  docker compose ps -a
  docker compose logs --tail 60 db app
  pause & exit /b 1
)

docker compose ps
echo.
echo   ArtistikCity is running in Docker
echo     Website      : http://localhost:8080/
echo     Student login: http://localhost:8080/login        student@artistikcity.com / password
echo     Admin panel  : http://localhost:8080/admin/login  admin@artistikcity.com / password
echo     MySQL        : localhost:3306   database artistikcity   user artistik   password Artistik@2026
echo                    root password ArtistikRoot@2026
echo.
echo   Logs: docker compose logs -f app      Stop: DOCKER-STOP.bat
start "" http://localhost:8080/
pause

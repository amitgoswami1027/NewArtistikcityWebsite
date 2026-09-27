@echo off
REM Stops the ArtistikCity containers. The database and uploads are kept in Docker volumes.
REM To delete all data as well:  docker compose down -v
cd /d "%~dp0"
docker compose down
pause

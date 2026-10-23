---
description: Build and start ArtistikCity against the local MySQL, then smoke-test the main pages
---
1. Make sure MySQL is running. On Windows run `START-MySQL.bat` (or `powershell -File setup-and-run.ps1 -DatabaseOnly`).
   It sets up the portable MySQL in `.tools\` the first time. Elsewhere, MySQL must listen on 127.0.0.1:3306 with database
   `artistikcity` and user `artistik` / `Artistik@2026`, or set DB_URL / DB_USERNAME / DB_PASSWORD.
2. Build: `mvn -B -q package -DskipTests`. If it fails, show the first compiler error and stop.
3. Start the app from the repo root in the background:
   `java -jar target/artistikcity-1.0.0.jar --spring.profiles.active=dev` (add `--server.port=$ARGUMENTS` if a port was given).
   Wait for `Started ArtistikCityApplication`. If it prints `APPLICATION FAILED` or an exception (for example "Access denied" or
   "Communications link failure", which mean MySQL isn't running or the login is wrong), report it.
4. Smoke-test with curl and report the HTTP status of each: `/`, `/courses`, `/login`, `/admin/login`.
5. Remind me of the demo logins (password `password`): student@artistikcity.com and admin@artistikcity.com.

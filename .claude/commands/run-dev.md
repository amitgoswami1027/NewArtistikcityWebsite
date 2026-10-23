---
description: Build and start ArtistikCity on the in-memory demo database, then smoke-test the main pages
---
1. Build: `mvn -B -q package -DskipTests`. If it fails, show the first compiler error and stop.
2. Start the app from the repo root in the background:
   `java -jar target/artistikcity-1.0.0.jar --spring.profiles.active=dev` (add `--server.port=$ARGUMENTS` if a port was given).
   Wait for `Started ArtistikCityApplication` in the output. If it prints `APPLICATION FAILED` or an exception, report it.
3. Smoke-test with curl and report the HTTP status of each: `/`, `/courses`, `/login`, `/admin/login`.
4. Remind me of the demo logins (password `password`): student@artistikcity.com and admin@artistikcity.com.

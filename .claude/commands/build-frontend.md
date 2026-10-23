---
description: Rebuild the React/Inertia bundle after changing files under frontend/resources/js
---
1. `cd frontend && npm install --no-audit --no-fund` (skip this step if `node_modules` is already present and package.json is unchanged).
2. Build with `npm run dev`, or with `npm run prod` if I asked for a production build.
3. Confirm that `src/main/resources/static/js/app.js` was updated (check its modification time). Report any webpack errors with the file and line.
4. If the app is running with the dev profile, tell me to hard-refresh the browser. Static assets are served from the classpath, so a running jar needs `mvn package` and a restart to pick up the change.

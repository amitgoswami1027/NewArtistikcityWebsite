// @ts-check
const { defineConfig, devices } = require('@playwright/test');

/**
 * Run against a local app started with the dev profile (test payments on, demo data loaded):
 *   START-ArtistikCity.bat            (or: mvn spring-boot:run -Dspring-boot.run.profiles=dev)
 *   cd tests/e2e && npm install && npx playwright install chromium && npm test
 * E2E_BASE_URL points the tests at another server.
 */
module.exports = defineConfig({
    testDir: './specs',
    timeout: 60_000,
    expect: { timeout: 8_000 },
    fullyParallel: false,
    workers: 1,
    retries: process.env.CI ? 1 : 0,
    reporter: [['list']],
    use: {
        baseURL: process.env.E2E_BASE_URL || 'http://localhost:8080',
        trace: 'retain-on-failure',
        screenshot: 'only-on-failure',
    },
    projects: [
        { name: 'desktop', use: { ...devices['Desktop Chrome'], viewport: { width: 1366, height: 900 } }, grepInvert: /@mobile/ },
        { name: 'mobile', use: { ...devices['Pixel 7'] }, grep: /@mobile/ },
    ],
});

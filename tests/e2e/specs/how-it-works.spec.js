// /how-it-works: the built-in page (7-step journey tracker and timeline, ending with selling in the marketplace).
// The showcase landing page replaces it while published (landing-showcase.spec.js), so these tests unpublish that
// landing page first (the original page comes back) and publish it again afterwards.
const { test, expect } = require('@playwright/test');
const { api } = require('./helpers');

let admin;
let landingId = null;

test.beforeAll(async ({ browser }) => {
    admin = await browser.newPage();
    await admin.goto('/admin/login');
    await admin.fill('input[name=email]', 'admin@artistikcity.com');
    await admin.fill('input[name=password]', 'password');
    await admin.locator('button[type=submit]').click();
    await admin.waitForURL(/\/admin\/dashboard/);
    const r = await api(admin, 'GET', '/admin/api/landing/pages?status=PUBLISHED&per=100');
    const row = ((r.json && r.json.rows) || []).find((x) => x.kind === 'SITE' && x.site_route === 'how-it-works');
    if (row) {
        landingId = row.id;
        expect((await api(admin, 'POST', `/admin/api/landing/pages/${landingId}/unpublish`)).status).toBe(200);
    }
});

test.afterAll(async () => {
    if (landingId) await api(admin, 'POST', `/admin/api/landing/pages/${landingId}/publish`, { note: 'e2e: back after the built-in page tests' });
    await admin.close();
});

test.describe('How it works: seven-step journey (built-in page)', () => {
    test('the tracker lists the seven steps in order and jumps to Sell', async ({ page }) => {
        await page.goto('/how-it-works');
        const tracker = page.getByRole('navigation', { name: 'The student journey, step by step' });
        await expect(tracker.getByRole('listitem')).toHaveCount(7);
        await expect(tracker).toContainText(/Discover.*Join.*Choose a course.*Learn.*Submit.*Certify.*Sell your art/s);
        await tracker.getByRole('link', { name: /Step 7 of 7: Sell your art/ }).click();
        await expect(page).toHaveURL(/\/how-it-works#step-sell$/);
        await expect(tracker.getByRole('link', { name: /Step 7 of 7/ })).toHaveAttribute('aria-current', 'step');
        await expect(page.locator('#step-sell')).toBeInViewport();
    });

    test('step 7 quotes the real terms and links to the marketplace', async ({ page }) => {
        await page.goto('/how-it-works');
        const sell = page.locator('#step-sell');
        await expect(sell.getByRole('heading', { name: 'Launch your gallery & sell' })).toBeVisible();
        await expect(sell).toContainText(/You keep \d+%/);
        await expect(sell).toContainText(/Payable \d+ days after the sale/);
        await expect(sell.getByRole('link', { name: /Explore the Marketplace/ })).toHaveAttribute('href', '/marketplace');
        await expect(page.locator('.hiw-timeline > li')).toHaveCount(7);
    });

    test('no sideways scrolling on a phone', async ({ browser }) => {
        const page = await browser.newPage({ viewport: { width: 375, height: 812 } });
        await page.goto('/how-it-works');
        expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(375);
        await page.close();
    });
});

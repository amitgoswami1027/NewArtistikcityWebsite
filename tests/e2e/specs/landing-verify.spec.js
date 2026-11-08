// Login & verification: Admin › Landing Page Management › Login & verification, then a brand-new visitor buys on the
// landing page without signing in first: details, Pay, a simulated SMS code ("Use this code"), payment, the
// congratulations screen and "Log in to My studio". Runs against the dev profile with a fresh database.
const { test, expect } = require('@playwright/test');
const { api } = require('./helpers');

const PAGE = '/landing/watercolour-landscapes-for-beginners';

async function adminSignIn(page) {
    await page.goto('/admin/login');
    await page.fill('input[name=email]', 'admin@artistikcity.com');
    await page.fill('input[name=password]', 'password');
    await page.locator('button[type=submit]').click();
    await page.waitForURL(/\/admin\/dashboard/);
}

test.describe.serial('Login & verification', () => {
    test('admin: the Login & verification tab switches on simulated SMS codes', async ({ page }) => {
        await adminSignIn(page);
        await page.goto('/admin/landing/login');
        await expect(page.getByTestId('lp-login')).toBeVisible();
        await expect(page.locator('.lpa-tabs a[aria-current=page]')).toContainText('Login & verification');
        const sim = page.locator('[data-field=sim-phone] input');
        if (!(await sim.isChecked())) await sim.click({ force: true });
        await expect(sim).toBeChecked();
        await expect(page.locator('.adm-alert')).toContainText('Simulated codes are on');
        // the page editor's Checkout step has the per-page choice
        await page.goto('/admin/landing/pages/1/checkout');
        await expect(page.getByTestId('lp-verify-with')).toHaveValue('DEFAULT');
    });

    test('a new visitor buys without signing in first, then opens My studio', async ({ page }) => {
        await page.goto(PAGE);
        await page.locator('.lp-hero a[data-cta=hero]').click();
        await expect(page.getByTestId('lp-co-guest')).toBeVisible();
        await expect(page.getByTestId('lp-co-verify-hint')).toContainText('text a 6-digit code');
        await page.fill('#lp-co-fname', 'Rhea Kapoor');
        await page.fill('#lp-co-femail', `rhea.${Date.now()}@example.com`);
        await page.fill('#lp-co-fphone', `97${String(Date.now()).slice(-8)}`);
        await page.getByTestId('lp-co-terms').check();
        await page.getByTestId('lp-co-pay').click();
        await expect(page.locator('#lp-co-h')).toHaveText('Confirm it\'s you');
        await expect(page.getByTestId('lp-co-simcode')).toContainText('Test mode · simulated SMS code');
        await page.getByTestId('lp-co-usecode').click();
        await page.getByTestId('lp-co-test-complete').click();                   // dev profile: test payment step
        await expect(page.getByTestId('lp-co-congrats')).toHaveText('Congratulations, Rhea!');
        await expect(page.getByTestId('lp-co-result')).toContainText('Watercolour');
        await page.getByTestId('lp-co-studio').click();
        await page.waitForURL(/\/dashboard/);
    });

    test('the log-in page shows the simulated SMS code', async ({ page, context }) => {
        await context.clearCookies();
        await page.goto('/login');
        await page.locator('button[role=tab]', { hasText: 'Phone' }).click();
        await page.fill('#f-phone', `96${String(Date.now()).slice(-8)}`);
        await page.locator('button[type=submit]', { hasText: 'Send code' }).click();
        await expect(page.getByTestId('simulated-code')).toContainText('Test mode · simulated SMS code');
    });

    test.afterAll(async ({ browser }) => {
        const page = await browser.newPage();
        await adminSignIn(page);
        await api(page, 'PUT', '/admin/api/landing/login', { simulatePhone: false });
        await page.close();
    });
});

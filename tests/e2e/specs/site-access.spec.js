// Admin > System parameters > Site access (/admin/parameters): switch the site password on, see the wall as a visitor,
// get in with the password, then switch it off again. Leaves the site open.
const { test, expect } = require('@playwright/test');

const PW = `Gallery-${Date.now()}`;      // a new one each run (the same password twice is refused)

async function adminSignIn(page) {
    await page.goto('/admin/login');
    await page.fill('input[name=email]', 'admin@artistikcity.com');
    await page.fill('input[name=password]', 'password');
    await page.locator('button[type=submit]').click();
    await page.waitForURL(/\/admin\/dashboard/);
}

test.describe('Admin: Site access password', () => {
    test('sidebar link, validation, wall for visitors, unlock, switch off', async ({ page, browser }) => {
        await adminSignIn(page);
        const toggle = page.getByRole('button', { name: /System Parameters/ });
        if ((await toggle.getAttribute('aria-expanded')) === 'false') await toggle.click();
        await page.getByRole('link', { name: 'Site Access Password' }).click();
        await expect(page).toHaveURL(/\/admin\/parameters$/);
        await expect(page.getByRole('heading', { name: 'Global Site Access Password' })).toBeVisible();
        await expect(page.getByText('Enable/Disable Site Password Protection')).toBeVisible();

        // a confirmation that doesn't match is refused next to the field, nothing changes
        const sw = page.getByRole('switch');
        await sw.check({ force: true });
        await page.fill('#sa-password', PW);
        await page.fill('#sa-confirm', `${PW}x`);
        await page.getByRole('button', { name: 'Save site access' }).click();
        await expect(page.locator('#sa-confirm-err')).toContainText("don't match");
        expect((await (await browser.newContext()).newPage().then((p) => p.goto('/courses'))).status()).toBe(200);

        await page.fill('#sa-password', PW);
        await page.fill('#sa-confirm', PW);
        await page.getByRole('button', { name: 'Save site access' }).click();
        await expect(page.getByRole('status')).toContainText('protection is on');
        await expect(page.getByText('Protected: password required', { exact: true })).toBeVisible();

        // a visitor sees only the wall, on a phone too
        const visitor = await browser.newContext({ viewport: { width: 390, height: 844 } });
        const v = await visitor.newPage();
        const res = await v.goto('/marketplace');
        expect(res.status()).toBe(401);
        await expect(v.getByRole('heading', { name: 'Site under construction' })).toBeVisible();
        expect(await v.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
        await v.fill('input[name=password]', 'not-the-password');
        await v.getByRole('button', { name: 'Enter' }).click();
        await expect(v.getByRole('alert')).toContainText("isn't right");
        await v.fill('input[name=password]', PW);
        await v.getByRole('button', { name: 'Enter' }).click();
        await expect(v).toHaveURL(/\/marketplace$/);
        await expect(v.locator('[data-page]')).toHaveCount(1);
        await visitor.close();

        // the admin still sees the site, then opens it to everyone
        expect((await page.request.get('/courses')).status()).toBe(200);
        await sw.uncheck({ force: true });
        await page.getByRole('button', { name: 'Save site access' }).click();
        await expect(page.getByText('Open to everyone', { exact: true })).toBeVisible();
        const fresh = await browser.newContext();
        expect((await (await fresh.newPage()).goto('/marketplace')).status()).toBe(200);
        await fresh.close();
    });
});

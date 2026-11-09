// SEO & Layout Controls page tabs (Our vision, Instructor, Customer support, FAQ) and the public pages they drive.
// Dev profile, fresh DB. Every test that saves something puts the default back at the end.
const { test, expect } = require('@playwright/test');

async function adminSignIn(page) {
    await page.goto('/admin/login');
    await page.fill('input[name=email]', 'admin@artistikcity.com');
    await page.fill('input[name=password]', 'password');
    await page.locator('button[type=submit]').click();
    await page.waitForURL(/\/admin\/dashboard/);
}

test.describe('Information pages', () => {
    test('public pages: content, working links, no "#" links, 2021 copyright', async ({ page }) => {
        for (const [path, testid] of [['/artistikcity-vision', 'info-vision'], ['/instructor', 'info-instructor'], ['/customer-support', 'info-support'], ['/faq', 'info-faq']]) {
            await page.goto(path);
            await expect(page.getByTestId(testid)).toBeVisible();
            await expect(page.locator('main a[href="#"]')).toHaveCount(0);
            await expect(page.locator('footer')).toContainText('© 2021 ArtistikCity. All rights reserved.');
        }
        await page.goto('/instructor');
        const links = page.getByTestId('instructor-links').locator('a');
        expect(await links.count()).toBeGreaterThan(0);
        for (const a of await links.all()) expect(await a.getAttribute('href')).toMatch(/^https:\/\//);
    });

    test('FAQ: search filters, help centre search lands on the FAQ', async ({ page }) => {
        await page.goto('/customer-support');
        await page.getByPlaceholder('Search the FAQ').fill('certificate');
        await page.getByRole('button', { name: 'Search' }).click();
        await page.waitForURL(/\/faq\?q=certificate/);
        await expect(page.getByTestId('faq-search')).toHaveValue('certificate');
        await expect(page.locator('.ip-faq')).not.toHaveCount(0);
        await page.getByTestId('faq-search').fill('zzzz-no-such-thing');
        await expect(page.getByTestId('faq-none')).toBeVisible();
    });

    test('admin: tabs, /admin/faq opens the FAQ tab, saving the vision heading shows on the site', async ({ page, isMobile }) => {
        test.skip(isMobile, 'desktop admin');
        await adminSignIn(page);
        await page.goto('/admin/faq');
        await expect(page).toHaveURL(/\/admin\/setup\/site-settings#faq$/);
        await expect(page.getByTestId('tab-faq')).toBeVisible();
        await expect(page.getByTestId('faq-manager')).toBeVisible();
        await page.getByTestId('ss-tab-vision').click();
        await expect(page).toHaveURL(/#vision$/);
        const heading = page.locator('#f-vision-heading');
        await heading.fill('Our vision for every artist');
        await page.getByTestId('tab-vision').getByRole('button', { name: 'Save changes' }).click();
        await expect(page.getByText('Saved. The page shows it straight away.')).toBeVisible();
        await page.goto('/artistikcity-vision');
        await expect(page.getByRole('heading', { level: 1 })).toHaveText('Our vision for every artist');
        // back to the default
        await page.goto('/admin/setup/site-settings#vision');
        await page.locator('#f-vision-heading').fill('');
        await page.getByTestId('tab-vision').getByRole('button', { name: 'Save changes' }).click();
        await expect(page.getByText('Saved. The page shows it straight away.')).toBeVisible();
        await page.getByTestId('ss-tab-layout').click();
        await expect(page.locator('#sec-landing')).toBeVisible();
    });
});

// Admin > System parameters (Configuration Panel, SEO & Layout Controls with the live preview) and Studio Stories.
// Read-only towards saved settings except where a test puts things back.
const { test, expect } = require('@playwright/test');

async function adminSignIn(page) {
    await page.goto('/admin/login');
    await page.fill('input[name=email]', 'admin@artistikcity.com');
    await page.fill('input[name=password]', 'password');
    await page.locator('button[type=submit]').click();
    await page.waitForURL(/\/admin\/dashboard/);
}

test.describe('Admin: System parameters and Studio Stories', () => {
    test.beforeEach(async ({ page }) => adminSignIn(page));

    test('sidebar: System Parameters group holds the three setup pages', async ({ page }) => {
        const nav = page.getByRole('navigation', { name: 'Admin navigation' }).or(page.locator('.adm-side__nav'));
        const toggle = page.getByRole('button', { name: /System Parameters/ });
        await expect(toggle).toBeVisible();
        if ((await toggle.getAttribute('aria-expanded')) === 'false') await toggle.click();
        await expect(toggle).toHaveAttribute('aria-expanded', 'true');
        for (const label of ['Configuration Panel', 'Room Preview Setup', 'SEO & Layout Controls']) {
            await expect(nav.getByRole('link', { name: label })).toBeVisible();
        }
        await expect(nav.getByRole('link', { name: 'Room preview', exact: true })).toHaveCount(0);   // moved out of Sales
        await nav.getByRole('link', { name: 'Room Preview Setup' }).click();
        await expect(page).toHaveURL(/\/admin\/setup\/room-preview$/);
        await expect(page.getByRole('heading', { name: 'Room Preview Setup' })).toBeVisible();
    });

    test('Configuration Panel: tabs, masked secrets, Stripe not connected, inline errors', async ({ page }) => {
        await page.goto('/admin/setup');
        await expect(page.getByRole('heading', { name: 'Configuration Panel' })).toBeVisible();
        await expect(page.getByRole('tab', { name: /Payments/ })).toHaveAttribute('aria-selected', 'true');
        await expect(page.getByRole('region', { name: 'Stripe' }).getByText('Not connected', { exact: true })).toBeVisible();
        await page.getByRole('tab', { name: /Google sign-in/ }).click();
        const secret = page.locator('[id="cfg-social.google.client-secret"]');
        await expect(secret).toHaveAttribute('type', 'password');
        await expect(secret).toHaveValue('•'.repeat(8));
        await page.getByRole('tab', { name: /Payments/ }).click();
        await page.locator('[id="cfg-razorpay.key"]').fill('not-a-key');
        await page.getByRole('button', { name: /Save 1 change/ }).click();
        await expect(page.locator('[id="cfg-razorpay.key-err"]')).toContainText('rzp_live_');
        await page.getByRole('button', { name: 'Discard' }).click();
        await expect(page.getByText('All changes saved')).toBeVisible();
    });

    test('SEO & Layout Controls: the preview follows typing, desktop and mobile', async ({ page }) => {
        await page.goto('/admin/setup/site-settings');
        const preview = page.locator('.ss-sim');
        await page.locator('[id="f-landing.hero_heading"]').fill('Preview heading 42');
        await expect(preview.locator('h1')).toContainText('Preview heading 42');
        await page.getByRole('button', { name: /Mobile/ }).click();
        await expect(preview.locator('.ss-site.is-mobile')).toBeVisible();
        await preview.locator('.ss-site__burger').click();
        await expect(preview.locator('.ss-site__drawer')).toBeVisible();
        await page.getByRole('button', { name: /Desktop/ }).click();
        // menu builder: add a link, it shows in the preview header; hide it, it goes
        await page.getByRole('button', { name: 'Add link' }).click();
        const last = page.locator('.ss-menu__item').last();
        await last.getByRole('textbox').first().fill('Gift cards');
        await last.getByRole('textbox').nth(1).fill('/gift-cards');
        await expect(preview.locator('.ss-site__nav')).toContainText('Gift cards');
        await last.getByLabel('Show').uncheck();
        await expect(preview.locator('.ss-site__nav')).not.toContainText('Gift cards');
        // a bad CTA link is refused inline, nothing saved
        await page.locator('[id="f-landing.cta_label"]').fill('Go');
        await page.locator('[id="f-landing.cta_link"]').fill('javascript:alert(1)');
        await page.getByRole('button', { name: 'Save changes' }).click();
        await expect(page.getByText('Use a page on this site')).toBeVisible();
        await page.getByRole('button', { name: 'Discard changes' }).click();
    });

    test('Studio Stories: dashboard boxes, filters, and the editor workflow', async ({ page }) => {
        await page.goto('/admin/content/stories');
        await expect(page.getByRole('heading', { name: 'Studio Stories' })).toBeVisible();
        const kpis = page.getByRole('region', { name: 'Content analytics' }).or(page.locator('.st-kpis'));
        await expect(kpis).toContainText('Views');
        await expect(kpis).toContainText('Engagement velocity');
        await expect(kpis).toContainText('Average read time');
        await page.getByRole('tab', { name: /Draft/ }).click();
        await expect(page).toHaveURL(/status=draft/);
        await page.goto('/admin/content/stories/new');
        await page.locator('#f-title').fill('E2E draft story');
        await expect(page.getByRole('radio', { name: /Draft/ })).toHaveAttribute('aria-checked', 'true');
        await page.getByRole('radio', { name: /Scheduled/ }).click();
        await expect(page.locator('#f-publish_at')).toBeVisible();
        await page.getByRole('radio', { name: /Draft/ }).click();
        await page.locator('.st-editbar').getByRole('button', { name: 'Save draft' }).click();
        await expect(page).toHaveURL(/\/admin\/content\/stories\/\d+$/);
        await expect(page.locator('.st-editbar')).toContainText('Draft');
        // clean up
        const id = page.url().split('/').pop();
        await page.evaluate(async (pid) => {
            const t = decodeURIComponent((document.cookie.match(/XSRF-TOKEN=([^;]+)/) || [])[1] || '');
            await fetch(`/admin/api/content/stories/${pid}`, { method: 'DELETE', headers: { 'X-XSRF-TOKEN': t, Accept: 'application/json' } });
        }, id);
    });
});

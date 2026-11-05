// Admin console pages added by the production-readiness work: Security log (filters, JSON diff drawer, integrity
// check), Refunds, Seats & waitlist, Payouts & ledger, Reports & bans and the Tag & hierarchy manager.
const { test, expect } = require('@playwright/test');

const ADMIN = { email: 'admin@artistikcity.com', password: 'password' };

async function adminSignIn(page) {
    await page.goto('/admin/login');
    await page.fill('input[name=email]', ADMIN.email);
    await page.fill('input[name=password]', ADMIN.password);
    await page.locator('button[type=submit]').click();
    await page.waitForURL(/\/admin\/dashboard/);
}

test.describe('Admin: security and money modules', () => {
    test.beforeEach(async ({ page }) => adminSignIn(page));

    test('security log: sign-in is logged, filters narrow it, a change opens a side-by-side diff', async ({ page }) => {
        // make an UPDATE with before/after: rename a skill level and back
        await page.goto('/admin/catalog?type=skills');
        const first = page.locator('table tbody tr').first();
        const name = (await first.locator('.adm-linkbtn').innerText()).replace(/^└\s*/, '').trim();
        for (const next of [`${name} X`, name]) {
            await first.locator('.adm-linkbtn').click();
            await page.locator('#f-name').fill(next);
            await page.getByRole('button', { name: 'Save' }).click();
            await expect(page.getByRole('dialog')).toHaveCount(0);
        }
        await page.goto('/admin/security/audit');
        await expect(page.getByRole('heading', { name: 'Security log' })).toBeVisible();
        await expect(page.locator('table')).toContainText('admin_login');
        await page.getByRole('group', { name: 'Action types' }).getByRole('button', { name: 'UPDATE' }).click();
        await expect(page).toHaveURL(/types=UPDATE/);
        await expect(page.locator('table tbody tr').first()).toContainText('UPDATE');
        await page.locator('table tbody tr').first().getByRole('button', { name: /^Open entry/ }).click();
        const drawer = page.getByRole('dialog');
        await expect(drawer.getByRole('table', { name: 'Before and after' })).toBeVisible();
        await expect(drawer).toContainText('name');
        await page.keyboard.press('Escape');
        await page.getByRole('button', { name: 'Verify integrity' }).click();
        await expect(page.getByRole('status').filter({ hasText: 'Hash chain intact' })).toBeVisible();
    });

    test('the money and community pages load', async ({ page }) => {
        for (const [url, heading] of [['/admin/payments/refunds', 'Refunds'], ['/admin/seats', 'Seats & waitlist'], ['/admin/finance', 'Payouts & ledger'],
            ['/admin/moderation', 'Reports & bans']]) {
            await page.goto(url);
            await expect(page.getByRole('heading', { name: heading, level: 1 })).toBeVisible();
            await expect(page.locator('.adm-crash')).toHaveCount(0);
        }
        await page.goto('/admin/finance');
        for (const tab of ['Report', 'Payouts', 'Ledger']) await page.getByRole('tab', { name: new RegExp(`^${tab}`) }).click();
    });

    test('tag manager: nest a genre under another, archive and restore it', async ({ page }) => {
        await page.goto('/admin/catalog?type=genres');
        const rows = page.locator('table tbody tr');
        const child = (await rows.nth(1).locator('.adm-linkbtn').innerText()).replace(/^└\s*/, '').trim();
        const parent = (await rows.nth(0).locator('.adm-linkbtn').innerText()).replace(/^└\s*/, '').trim();
        await rows.nth(1).locator('.adm-linkbtn').click();
        await page.getByLabel('Sits under').selectOption({ label: parent });
        await page.getByRole('button', { name: 'Save' }).click();
        await expect(page.locator('table')).toContainText(`${parent} › ${child}`);
        await page.getByRole('button', { name: `Archive ${child}` }).click();
        await expect(page.locator('table')).not.toContainText(`${parent} › ${child}`);
        await page.getByLabel(/Show archived/).check();
        const row = page.locator('table tbody tr', { hasText: child });
        await expect(row).toContainText('Archived');
        await row.getByRole('button', { name: 'Restore' }).click();
        await expect(page.locator('table tbody tr', { hasText: child })).not.toContainText('Archived');
    });
});

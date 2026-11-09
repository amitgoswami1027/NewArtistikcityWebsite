// Landing page A/B test from the editor: change the draft, start a 50/50 test, see the figures, view A and B, promote B
// (it replaces the live page), then put the original back. Dev profile, fresh DB (seed page "watercolour-landscapes-for-beginners").
const { test, expect } = require('@playwright/test');

const SLUG = 'watercolour-landscapes-for-beginners';
const NEW = 'A bolder headline for B';

async function adminSignIn(page) {
    await page.goto('/admin/login');
    await page.fill('input[name=email]', 'admin@artistikcity.com');
    await page.fill('input[name=password]', 'password');
    await page.locator('button[type=submit]').click();
    await page.waitForURL(/\/admin\/dashboard/);
}
async function api(page, method, url, data) {
    const tok = decodeURIComponent((await page.context().cookies()).find((c) => c.name === 'XSRF-TOKEN').value);
    const r = await page.request.fetch(url, { method, data, headers: { 'X-XSRF-TOKEN': tok, Accept: 'application/json' } });
    return r.json();
}

test.describe.serial('Landing A/B test', () => {
    test('start in the editor, see the split, promote B, restore', async ({ page, browser, isMobile }) => {
        test.skip(isMobile, 'desktop admin');
        await adminSignIn(page);
        const list = await api(page, 'GET', '/admin/api/landing/pages');
        const row = (list.rows || list.pages || []).find((p) => p.slug === SLUG);
        expect(row).toBeTruthy();
        const id = row.id;
        let d = await api(page, 'GET', `/admin/api/landing/pages/${id}`);
        const original = JSON.parse(JSON.stringify(d.config));
        const cfg = d.config;
        cfg.sections.find((s) => s.type === 'hero').content.heading = NEW;
        await api(page, 'PUT', `/admin/api/landing/pages/${id}/draft`, { config: cfg, savedAt: d.page.draft_saved_at });

        await page.goto(`/admin/landing/pages/${id}/publish`);
        await page.getByTestId('ab-name').fill('Bolder headline');
        await page.getByTestId('ab-start').click();
        await expect(page.getByTestId('ab-running')).toBeVisible();
        await expect(page.getByTestId('ab-figures')).toBeVisible();
        await expect(page.getByTestId('lp-publish')).toBeDisabled();

        // visitors: each gets A or B and keeps it
        const seen = new Set();
        for (let i = 0; i < 12; i++) {
            const ctx = await browser.newContext();
            const v = await ctx.newPage();
            const r = await v.goto(`/landing/${SLUG}`);
            const arm = r.headers()['x-ab-variant'];
            seen.add(arm);
            if (arm === 'B') await expect(v.locator('body')).toContainText(NEW); else await expect(v.locator('body')).not.toContainText(NEW);
            expect((await v.goto(`/landing/${SLUG}`)).headers()['x-ab-variant']).toBe(arm);
            await ctx.close();
        }
        expect([...seen].sort()).toEqual(['A', 'B']);

        await page.reload();
        await expect(page.getByTestId('ab-A-visitors')).not.toHaveText('0');
        await page.getByTestId('ab-promote-b').click();
        await page.locator('.adm-overlay').getByRole('button', { name: 'Promote B', exact: true }).click();
        await expect(page.getByTestId('ab-running')).toHaveCount(0);
        const fresh = await (await browser.newContext()).newPage();
        const r = await fresh.goto(`/landing/${SLUG}`);
        await expect(fresh.locator('body')).toContainText(NEW);
        expect(r.headers()['x-ab-variant']).toBeUndefined();

        // put the original back
        d = await api(page, 'GET', `/admin/api/landing/pages/${id}`);
        await api(page, 'PUT', `/admin/api/landing/pages/${id}/draft`, { config: original, savedAt: d.page.draft_saved_at, force: true });
        await api(page, 'POST', `/admin/api/landing/pages/${id}/publish`, { note: 'Back to the original' });
    });
});

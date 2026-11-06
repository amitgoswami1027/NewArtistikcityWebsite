// My studio > Sell your art (journey step 7): the stepper, the page sections and the listing form's price maths.
const { test, expect } = require('@playwright/test');
const { signIn } = require('./helpers');

test.describe('My studio: Sell your art', () => {
    test.beforeEach(async ({ page }) => signIn(page));

    test('the studio shows the seven-step journey ending with Sell, and a way in', async ({ page }) => {
        await page.goto('/dashboard');
        const journey = page.getByRole('list', { name: 'Your creative journey' });
        await expect(journey.getByRole('listitem')).toHaveCount(7);
        await expect(journey).toContainText('Sell your art');
        await expect(page.getByRole('navigation', { name: 'Studio' }).getByRole('link', { name: /Sell your art/ })).toBeVisible();
        await page.getByRole('link', { name: 'Open Sell your art' }).click();
        await expect(page).toHaveURL(/\/dashboard\/sell$/);
        await expect(page.getByRole('heading', { name: 'Sell your art', level: 1 })).toBeVisible();
    });

    test('the page lists terms, listings, earnings and payout details', async ({ page }) => {
        await page.goto('/dashboard/sell');
        await expect(page.getByRole('list', { name: 'How selling works' })).toContainText(/You keep \d+%/);
        for (const h of ['Ready to sell', 'Your listings', 'Earnings', 'Payout details']) {
            await expect(page.getByRole('heading', { name: h })).toBeVisible();
        }
    });

    test('the listing form shows what the student earns', async ({ page }) => {
        await page.goto('/dashboard/sell');
        const edit = page.locator('.sl-listing').first().getByRole('link', { name: /Edit|Make changes/ });
        test.skip(await edit.count() === 0, 'no editable listing in this database');
        await edit.click();
        await page.locator('#f-basePrice').fill('5200');
        await expect(page.locator('.sl-earn .is-total')).toContainText('3,640');
        await page.getByRole('link', { name: 'Cancel' }).click();
        await expect(page).toHaveURL(/\/dashboard\/sell$/);
    });
});

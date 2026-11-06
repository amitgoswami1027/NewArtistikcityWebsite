// Marketplace: artist originals (default) and student paintings, switched with a toggle mirrored in ?source=student.
const { test, expect } = require('@playwright/test');

test.describe('Marketplace collections', () => {
    test('artist originals by default; the toggle shows only student paintings', async ({ page }) => {
        await page.goto('/marketplace');
        const toggle = page.getByRole('radiogroup', { name: 'Which paintings to show' });
        await expect(toggle.getByRole('radio', { name: /Artist originals/ })).toHaveAttribute('aria-checked', 'true');
        await expect(page.locator('.mk-card .mk-tag--student')).toHaveCount(0);
        const artists = await page.locator('.mk-card').count();
        expect(artists).toBeGreaterThan(0);

        await toggle.getByRole('radio', { name: /Student paintings/ }).click();
        await expect(page).toHaveURL(/[?&]source=student/);
        await expect(page.getByRole('heading', { level: 1 })).toContainText('Student paintings');
        const cards = page.locator('.mk-card');
        const n = await cards.count();
        if (n > 0) await expect(page.locator('.mk-card .mk-tag--student')).toHaveCount(n);
        else await expect(page.getByText('No student paintings for sale yet')).toBeVisible();

        await toggle.getByRole('radio', { name: /Artist originals/ }).click();
        await expect(page).not.toHaveURL(/source=student/);
        await expect(page.locator('.mk-card')).toHaveCount(artists);
    });

    test('a shared ?source=student link opens the student collection', async ({ page }) => {
        await page.goto('/marketplace?source=student');
        await expect(page.getByRole('radio', { name: /Student paintings/ })).toHaveAttribute('aria-checked', 'true');
    });

    test('the old /student-shop address lands on the student collection', async ({ page }) => {
        await page.goto('/student-shop');
        await expect(page).toHaveURL(/\/marketplace\?source=student/);
        await expect(page.getByRole('radio', { name: /Student paintings/ })).toHaveAttribute('aria-checked', 'true');
    });
});

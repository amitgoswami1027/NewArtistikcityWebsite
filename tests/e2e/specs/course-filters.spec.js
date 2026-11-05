// /courses: every filter change is written into the address bar, so a refresh or a shared link shows the same view.
const { test, expect } = require('@playwright/test');

const JUNIOR = 'Junior - 12 to 15 yrs';
const cards = (page) => page.locator('main a[href*="/course/"]');
const pill = (page, label) => page.locator('.dm-pill').filter({ has: page.locator('button', { hasText: new RegExp(`^${label}`) }) });

async function pick(page, label, option) {
    const p = pill(page, label);
    await p.locator('button').first().click();
    await p.getByLabel(option).check();
    await page.mouse.click(5, 5); // close the menu
}

test.describe('Course catalogue filters and the URL', () => {
    test('an age filter narrows the list and is written into the URL', async ({ page }) => {
        await page.goto('/courses?type=all');
        await expect(cards(page).first()).toBeVisible();
        const before = await cards(page).count();

        await pick(page, 'Age', JUNIOR);
        await expect(page).toHaveURL(/age=Junior/);
        const url = new URL(page.url());
        expect(url.searchParams.get('age')).toBe(JUNIOR);
        expect(url.searchParams.get('type')).toBe('all');
        const after = await cards(page).count();
        expect(after).toBeGreaterThan(0);
        expect(after).toBeLessThanOrEqual(before);
    });

    test('search text and sort go into the URL and survive a reload', async ({ page }) => {
        await page.goto('/courses?type=all');
        await page.getByLabel('Search in results').fill('watercolour');
        await page.getByLabel('Sort by').selectOption('newest');
        await expect(page).toHaveURL(/q=watercolour/);
        await expect(page).toHaveURL(/sort=newest/);
        const titles = await cards(page).allInnerTexts();

        await page.reload();
        await expect(page.getByLabel('Search in results')).toHaveValue('watercolour');
        await expect(page.getByLabel('Sort by')).toHaveValue('newest');
        expect(await cards(page).allInnerTexts()).toEqual(titles);
    });

    test('a shared link opens with the same filters ticked', async ({ page }) => {
        await page.goto(`/courses?type=all&age=${encodeURIComponent(JUNIOR)}`);
        const p = pill(page, 'Age');
        await expect(p.locator('button').first()).toHaveText(/Age \(1\)/);
        await p.locator('button').first().click();
        await expect(p.getByLabel(JUNIOR)).toBeChecked();
    });

    test('clearing a filter removes it from the URL and adds no history entries', async ({ page }) => {
        await page.goto('/courses?type=all');
        const length = await page.evaluate(() => window.history.length);
        await pick(page, 'Age', JUNIOR);
        await expect(page).toHaveURL(/age=/);
        const p = pill(page, 'Age');
        await p.locator('button').first().click();
        await p.getByLabel(JUNIOR).uncheck();
        await expect(page).not.toHaveURL(/age=/);
        expect(await page.evaluate(() => window.history.length)).toBe(length);
    });
});

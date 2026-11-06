// /courses: progressive disclosure (Medium tabs, Format buttons and Age pills visible, Level / Subject in "More filters"), search that
// works with the filters (typo-tolerant), removable active-filter chips, a zero-results view that offers the change
// that brings results back, and every change mirrored into the address bar.
const { test, expect } = require('@playwright/test');

const JUNIOR = 'Junior - 12 to 15 yrs';
const cards = (page) => page.locator('main .dm-grid article');
const agePill = (page, label) => page.getByRole('group', { name: 'Age group' }).getByRole('button', { name: label });

test.describe('Course catalogue filters', () => {
    test('Medium, Format and Age are one click away; the drawer holds the rest', async ({ page }) => {
        await page.goto('/courses?type=all');
        await expect(page.getByRole('navigation', { name: 'Mediums' }).getByRole('button', { name: 'All' })).toBeVisible();
        for (const f of ['All formats', 'Live courses', 'Workshops']) await expect(page.getByRole('group', { name: 'Format' }).getByRole('button', { name: new RegExp(f) })).toBeVisible();
        await expect(agePill(page, JUNIOR)).toBeVisible();
        await expect(page.getByRole('dialog')).toHaveCount(0);
        await page.getByRole('button', { name: /More filters/ }).click();
        const dialog = page.getByRole('dialog', { name: 'More filters' });
        await expect(dialog).toBeVisible();
        for (const legend of ['Skill level', 'Subject']) await expect(dialog.getByRole('group', { name: legend })).toBeVisible();
        await page.keyboard.press('Escape');
        await expect(dialog).toHaveCount(0);
        await expect(page.getByRole('button', { name: /More filters/ })).toBeFocused();
    });

    test('an age pill narrows the list, shows a chip and is written into the URL', async ({ page }) => {
        await page.goto('/courses?type=all');
        await expect(cards(page).first()).toBeVisible();
        const before = await cards(page).count();
        await agePill(page, JUNIOR).click();
        await expect(agePill(page, JUNIOR)).toHaveAttribute('aria-pressed', 'true');
        await expect(page).toHaveURL(/age=Junior/);
        expect(new URL(page.url()).searchParams.get('age')).toBe(JUNIOR);
        await expect(page.getByRole('list', { name: 'Active filters' }).getByRole('button', { name: `Remove age filter ${JUNIOR}` })).toBeVisible();
        expect(await cards(page).count()).toBeLessThanOrEqual(before);
    });

    test('the Format buttons pick live courses or workshops; the count is announced', async ({ page }) => {
        await page.goto('/courses?type=all');
        const formats = page.getByRole('group', { name: 'Format' });
        const workshops = formats.getByRole('button', { name: /Workshops/ });
        const n = Number((await workshops.locator('.cf-format__n').innerText()).trim());
        await workshops.click();
        await expect(workshops).toHaveAttribute('aria-pressed', 'true');
        await expect(page).toHaveURL(/type=workshop/);
        await expect(page.locator('[aria-live="polite"]').first()).toHaveText(new RegExp(`^${n} course`));
        await expect(cards(page)).toHaveCount(n);
        await formats.getByRole('button', { name: /All formats/ }).click();
        await expect(page).not.toHaveURL(/type=workshop/);
    });

    test('search tolerates typos and works inside the active filters', async ({ page }) => {
        await page.goto('/courses?type=all');
        await page.getByLabel('Search in results').fill('watercolor');            // US spelling
        await expect(cards(page).first()).toContainText(/Watercolour/i);
        await page.getByLabel('Search in results').fill('pencl skeching');       // two typos
        await expect(cards(page).first()).toContainText(/Pencil Sketching/i);
        await expect(page).toHaveURL(/q=pencl/);
    });

    test('no results: one-click fixes and Clear filters', async ({ page }) => {
        await page.goto('/courses?type=all');
        await agePill(page, JUNIOR).click();
        await page.getByLabel('Search in results').fill('pencil');
        // pencil sketching is for another age group: nothing matches both
        await expect(page.getByRole('heading', { name: 'No courses match your filters' })).toBeVisible();
        const fix = page.getByRole('button', { name: /^Remove age “Junior/ });
        await expect(fix).toBeVisible();
        await fix.click();
        await expect(cards(page).first()).toContainText(/Pencil/i);
        await page.getByRole('button', { name: 'Clear filters' }).first().click();
        await expect(page).toHaveURL(/\/courses\?type=all$/);
    });

    test('search and sort survive a reload; a shared link restores the pills', async ({ page }) => {
        await page.goto('/courses?type=all');
        await page.getByLabel('Search in results').fill('watercolour');
        await page.getByLabel('Sort by').selectOption('newest');
        await expect(page).toHaveURL(/sort=newest/);
        const titles = await cards(page).allInnerTexts();
        await page.reload();
        await expect(page.getByLabel('Search in results')).toHaveValue('watercolour');
        expect(await cards(page).allInnerTexts()).toEqual(titles);
        await page.goto(`/courses?type=all&age=${encodeURIComponent(JUNIOR)}`);
        await expect(agePill(page, JUNIOR)).toHaveAttribute('aria-pressed', 'true');
    });

    test('filter changes add no history entries', async ({ page }) => {
        await page.goto('/courses?type=all');
        const length = await page.evaluate(() => window.history.length);
        await agePill(page, JUNIOR).click();
        await agePill(page, JUNIOR).click();
        await expect(page).not.toHaveURL(/age=/);
        expect(await page.evaluate(() => window.history.length)).toBe(length);
    });

    test('@mobile pills wrap, the drawer fills the screen', async ({ page }) => {
        await page.goto('/courses?type=all');
        await page.getByRole('button', { name: /More filters/ }).click();
        const box = await page.getByRole('dialog', { name: 'More filters' }).boundingBox();
        const vw = page.viewportSize().width;
        expect(box.width).toBeGreaterThan(vw * 0.9);
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1)).toBe(true);
    });
});

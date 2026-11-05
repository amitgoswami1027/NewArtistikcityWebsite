// /marketplace: 4 columns on desktop, 2 on tablets, 1 on phones; lazy-loaded images; cards line up.
const { test, expect } = require('@playwright/test');

const columns = (page) => page.getByTestId('mk-grid').evaluate((g) => getComputedStyle(g).gridTemplateColumns.split(' ').length);

test.describe('Marketplace grid', () => {
    for (const [width, cols] of [[1440, 4], [1024, 4], [820, 2], [640, 2]]) {
        test(`${width}px wide shows ${cols} columns`, async ({ page }) => {
            await page.setViewportSize({ width, height: 900 });
            await page.goto('/marketplace');
            await expect(page.getByTestId('mk-grid')).toBeVisible();
            expect(await columns(page)).toBe(cols);
            const tops = await page.evaluate((n) => [...document.querySelectorAll('[data-testid=mk-grid] > *')].slice(0, n)
                .map((e) => Math.round(e.getBoundingClientRect().top)), cols);
            expect(new Set(tops).size).toBe(1);
        });
    }

    test('images load lazily and keep their proportions', async ({ page }) => {
        await page.goto('/marketplace');
        const imgs = page.locator('.mk-card__img');
        expect(await imgs.count()).toBeGreaterThan(0);
        for (const img of await imgs.all()) {
            await expect(img).toHaveAttribute('loading', 'lazy');
            expect(Number(await img.getAttribute('width'))).toBeGreaterThan(0);
            expect(Number(await img.getAttribute('height'))).toBeGreaterThan(0);
        }
    });

    test('phone: one column, no sideways scrolling @mobile', async ({ page }) => {
        await page.goto('/marketplace');
        await expect(page.getByTestId('mk-grid')).toBeVisible();
        expect(await columns(page)).toBe(1);
        const overflow = await page.evaluate(() => Math.max(document.documentElement.scrollWidth, window.innerWidth) - document.documentElement.clientWidth);
        expect(overflow).toBeLessThanOrEqual(1);
    });
});

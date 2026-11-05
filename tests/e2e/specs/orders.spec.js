// Your orders: courses, workshops, marketplace orders and commissions in one list, newest first, with filters.
const { test, expect } = require('@playwright/test');
const { signIn, buyPoster, api } = require('./helpers');

test.describe('Your orders', () => {
    test('the unified endpoint answers 200 with course and marketplace orders, newest first', async ({ page }) => {
        await signIn(page);
        const orderId = await buyPoster(page);
        const { status, json } = await api(page, 'GET', '/api/v1/user/orders');
        expect(status).toBe(200);
        const types = new Set(json.orders.map((o) => o.orderType));
        expect(types.has('course')).toBe(true);
        expect(types.has('marketplace')).toBe(true);
        const dates = json.orders.map((o) => o.purchaseDate);
        expect([...dates].sort().reverse()).toEqual(dates);
        const poster = json.orders.find((o) => o.orderId === orderId);
        expect(poster).toMatchObject({ orderType: 'marketplace', category: 'Poster Print', status: 'processing' });
        for (const o of json.orders) {
            expect(Object.keys(o)).toEqual(expect.arrayContaining(['orderId', 'orderType', 'title', 'purchaseDate', 'amount', 'status']));
            expect(['pending', 'processing', 'shipped', 'completed', 'failed', 'refunded']).toContain(o.status);
        }
    });

    test('signed out, the endpoint answers 401', async ({ request }) => {
        const r = await request.get('/api/v1/user/orders', { headers: { Accept: 'application/json' } });
        expect(r.status()).toBe(401);
    });

    test('the page shows badges and the right action for each kind of order', async ({ page }) => {
        await signIn(page);
        await buyPoster(page);
        await page.goto('/user/orders');
        const course = page.locator('.ord-item[data-category="Digital Course"]').first();
        await expect(course.locator('.ord-badge')).toHaveText(/Digital Course/);
        await expect(course.locator('[data-action=learn]')).toHaveText(/Start learning/);
        const poster = page.locator('.ord-item[data-category="Poster Print"]').first();
        await expect(poster.locator('.ord-badge')).toHaveText(/Poster Print/);
        await expect(poster.locator('[data-action=detail]')).toBeVisible();
    });

    test('filter chips and search narrow the list, and the filter is kept in the address', async ({ page }) => {
        await signIn(page);
        await buyPoster(page);
        await page.goto('/user/orders');
        const all = await page.locator('.ord-item').count();
        await page.click('.ord-chip[data-type=course]');
        await expect(page).toHaveURL(/type=course/);
        const courses = page.locator('.ord-item');
        expect(await courses.count()).toBeLessThan(all);
        for (const c of await page.locator('.ord-item').all()) await expect(c).toHaveAttribute('data-category', 'Digital Course');

        await page.click('.ord-chip[data-type=print]');
        for (const c of await page.locator('.ord-item').all()) await expect(c).toHaveAttribute('data-category', /Canvas Print|Poster Print/);

        await page.click('.ord-chip[data-type=all]');
        await page.fill('.ord-search input', 'no-such-order-xyz');
        await expect(page.getByText('No orders match these filters.')).toBeVisible();
        await page.getByRole('button', { name: 'Clear filters' }).click();
        await expect(page.locator('.ord-item')).toHaveCount(all);

        await page.goto('/user/orders?type=marketplace');
        await expect(page.locator('.ord-chip[data-type=marketplace]')).toHaveAttribute('aria-pressed', 'true');
    });
});

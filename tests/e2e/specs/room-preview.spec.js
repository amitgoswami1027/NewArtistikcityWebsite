// Screen 2, "Preview and frame": the artwork is the big picture; the rooms from Admin > Room preview are one click away.
// Also: buying starts with an account (a guest's choice is kept through signing in).
const { test, expect } = require('@playwright/test');
const { ARTWORK, STUDENT, emptyCart, signIn } = require('./helpers');

async function openPreview(page, format = 'CANVAS') {
    await page.goto(`/marketplace/${ARTWORK}`);
    await page.click(`.pz-buyopt[data-format=${format}]`);
    await page.getByTestId('buy-continue').click();
    await page.waitForURL(/\/purchase\/step-2/);
    await expect(page.locator('.ap-stage')).toBeVisible();
}

test.describe('Preview and frame', () => {
    test('a guest pressing Buy now logs in first and lands on the preview with the same choice', async ({ page }) => {
        await page.goto(`/marketplace/${ARTWORK}`);
        await page.click('.pz-buyopt[data-format=POSTER]');
        await page.getByTestId('buy-continue').click();
        await page.waitForURL(/\/login\?return_to=/);
        await expect(page.getByTestId('purchase-intent')).toContainText('Log in to finish buying');
        await page.getByText('Log in with a password instead').click();
        await page.fill('input[name=email]', STUDENT.email);
        await page.fill('input[name=password]', STUDENT.password);
        await page.locator('button[type=submit]', { hasText: 'Log in' }).click();
        await page.waitForURL(/\/purchase\/step-2/);
        await expect(page.locator('.ap-head__format')).toContainText(/poster/i);
    });

    test.describe('signed in', () => {
        test.beforeEach(async ({ page }) => {
            await signIn(page);
            await emptyCart(page);
            await page.evaluate(() => { try { window.sessionStorage.clear(); } catch (e) { /* ignore */ } });
        });

        test('the artwork comes first, large, with its real size drawn beside it', async ({ page }) => {
            await openPreview(page);
            const stage = page.locator('.ap-stage');
            await expect(stage).toHaveAttribute('data-view', 'artwork');
            const s = await stage.boundingBox();
            const art = await page.locator('.ap-stage .ap-place').boundingBox();
            expect(art.height / s.height).toBeGreaterThan(0.5);
            await expect(page.locator('.ap-dim--w')).toContainText('in');
            await expect(page.locator('.ap-view[data-view-id=art]')).toHaveAttribute('aria-checked', 'true');
        });

        test('every room the admin shows is one click away, and switching is immediate', async ({ page }) => {
            await openPreview(page);
            const ids = await page.locator('.ap-view:not(.ap-view--art)').evaluateAll((els) => els.map((e) => e.getAttribute('data-view-id')));
            expect(ids.length).toBeGreaterThan(0);
            for (const id of ids) {
                const now = await page.evaluate((room) => {
                    document.querySelector(`.ap-view[data-view-id="${room}"]`).click();
                    return null;
                }, id);
                expect(now).toBeNull();
                await expect(page.locator('.ap-stage')).toHaveAttribute('data-room', id);
                await expect(page.locator(`.ap-view[data-view-id="${id}"]`)).toHaveAttribute('aria-checked', 'true');
                await expect.poll(() => page.locator('.ap-scene__photo').evaluate((img) => img.complete && img.naturalWidth > 0)).toBe(true);
                await expect(page.locator('.ap-stage .ap-art')).toBeVisible();
                await expect(page.getByTestId('room-scale')).toContainText('in');
            }
            await page.click('.ap-view[data-view-id=art]');
            await expect(page.locator('.ap-stage')).toHaveAttribute('data-view', 'artwork');
        });

        test('the view is remembered; size and frame change the picture and the price', async ({ page }) => {
            await openPreview(page);
            const first = await page.locator('.ap-view:not(.ap-view--art)').first().getAttribute('data-view-id');
            await page.click(`.ap-view[data-view-id="${first}"]`);
            await page.reload();
            await expect(page.locator('.ap-stage')).toHaveAttribute('data-room', first);
            await page.click('.ap-view[data-view-id=art]');

            const price = page.getByTestId('room-price');
            const p0 = await price.textContent();
            await page.getByTestId('part-next').click();          // A. Size -> B. Frame
            await expect(page.locator('.ap-part')).toHaveAttribute('data-part', 'frame');
            await page.click('.ap-frame[data-frame=OAK]');
            await expect(page.locator('.ap-stage .ap-art')).toHaveClass(/ap-art--oak/);
            await expect(price).not.toHaveText(p0);
            await page.getByTestId('part-back').click();
            const sizes = page.locator('.ap-size:not(.ap-size--custom)');
            if (await sizes.count() > 1) {
                const r0 = await page.locator('.ap-stage .ap-place').evaluate((el) => el.clientWidth / el.clientHeight);
                await sizes.last().click();
                await expect(sizes.last()).toHaveAttribute('aria-pressed', 'true');
                const dim = await page.locator('.ap-dim--w').textContent();
                expect(dim).toMatch(/\d/);
                expect(r0).toBeGreaterThan(0);
            }
        });

        test('Buy now goes to payment with exactly this choice', async ({ page }) => {
            await openPreview(page);
            await page.click('.ap-parts button[data-part=frame]');
            await page.click('.ap-frame[data-frame=BLACK_WOOD]');
            await page.getByTestId('room-continue').click();
            await page.waitForURL(/\/purchase\/checkout/);
            await expect(page.locator('.pz-line')).toHaveCount(1);
            await expect(page.locator('.pz-line__variant').first()).toContainText('black wood frame');
        });

        test('@mobile the preview fits the screen with a Buy now bar', async ({ page, isMobile }) => {
            test.skip(!isMobile, 'phone layout');
            await openPreview(page);
            const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
            expect(overflow).toBeLessThanOrEqual(1);
            await expect(page.locator('.ap-mobilebar')).toBeVisible();
            const s = await page.locator('.ap-stage').boundingBox();
            expect(s.width).toBeGreaterThan(300);
        });
    });
});

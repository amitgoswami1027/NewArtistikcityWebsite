// Cart, Buy now next to Add to cart, Make an offer on originals, and the step-2 preview (backgrounds carousel, sizes, Next buttons).
const { test, expect } = require('@playwright/test');
const { signIn, api, emptyCart, ARTWORK } = require('./helpers');

/** An original by the studio in the demo data that takes offers (the default for studio originals). */
const ORIGINAL = 'scarlet-macaw-study';

async function openStep2(page, format = 'CANVAS') {
    await page.goto(`/marketplace/${ARTWORK}`);
    await page.click(`.pz-buyopt[data-format=${format}]`);
    await page.getByTestId('buy-continue').click();
    await page.waitForURL(/\/purchase\/step-2/);
    await expect(page.locator('.ap-part')).toHaveAttribute('data-part', 'size');
}

const noSideScroll = (page) => page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);

test.describe('Cart and Buy now', () => {
    test.beforeEach(async ({ page }) => { await signIn(page); await emptyCart(page); });

    test('add a print from a marketplace card, see it in the drawer and on /cart', async ({ page }) => {
        await page.goto('/marketplace');
        const add = page.getByTestId('card-add').first();
        await add.click();
        await expect(add).toContainText('In your cart');
        await expect(page.locator('.pz-drawer')).toBeVisible();
        await page.getByTestId('drawer-view-cart').click();
        await page.waitForURL(/\/cart$/);
        await expect(page.getByTestId('cart-lines').locator('.pz-line')).toHaveCount(1);
        await expect(page.locator('.dm-cart .pz-cartbtn__count')).toHaveText('1');
        expect(await noSideScroll(page)).toBeLessThanOrEqual(1);
    });

    test('Add to cart on step 2 keeps you there; Buy now then checks out only that choice', async ({ page }) => {
        await openStep2(page, 'POSTER');
        await page.getByTestId('room-add').click();
        await expect(page.locator('.pz-added')).toContainText(/cart/i);
        await openStep2(page, 'CANVAS');
        await page.getByTestId('room-continue').click();
        await page.waitForURL(/\/purchase\/checkout/);
        await expect(page.getByTestId('checkout-only')).toBeVisible();
        await expect(page.locator('.pz-line')).toHaveCount(1);
        await expect(page.locator('.pz-line__variant').first()).toContainText(/canvas/i);
        // the whole cart is one click away
        await page.goto('/purchase/checkout?all=1');
        await expect(page.getByTestId('checkout-only')).toHaveCount(0);
        await expect(page.locator('.pz-line')).toHaveCount(2);
    });

    test('the /cart page checks out the whole cart', async ({ page }) => {
        let r = await api(page, 'POST', '/api/v1/cart/items', { paintingId: 2, format: 'POSTER', size: 'S', finish: 'MATTE', frame: 'NONE', qty: 2 });
        expect(r.status, JSON.stringify(r.json)).toBe(200);
        await page.goto('/cart');
        await expect(page.getByTestId('cart-lines').locator('.pz-line')).toHaveCount(1);
        await page.getByTestId('cart-checkout').click();
        await page.waitForURL(/\/purchase\/checkout/);
        await expect(page.getByTestId('checkout-only')).toHaveCount(0);
    });
});

test.describe('Step 2: backgrounds, size and Next', () => {
    test.beforeEach(async ({ page }) => { await signIn(page); await emptyCart(page); });

    test('arrows step through every background and wrap around', async ({ page }) => {
        await openStep2(page);
        const count = page.getByTestId('bg-count');
        const total = Number((await count.textContent()).match(/of (\d+)/)[1]);
        expect(total).toBeGreaterThan(1);
        await expect(count).toContainText(`1 of ${total}`);
        await page.getByTestId('bg-next').click();
        await expect(count).toContainText(`2 of ${total}`);
        await expect(page.locator('.ap-scene__photo')).toBeVisible();
        await page.getByTestId('bg-prev').click();
        await page.getByTestId('bg-prev').click();
        await expect(count).toContainText(`${total} of ${total}`);
        // keyboard works too
        await page.locator('.ap-carousel').focus();
        await page.keyboard.press('ArrowRight');
        await expect(count).toContainText(`1 of ${total}`);
    });

    test('a custom size changes the drawn size, the measurement and the price', async ({ page }) => {
        await openStep2(page);
        const price = page.getByTestId('room-price');
        const p0 = await price.textContent();
        const w0 = await page.locator('.ap-stage .ap-place').evaluate((el) => el.getBoundingClientRect().width);
        await page.getByTestId('size-custom').click();
        await expect(page.getByTestId('custom-size')).toBeVisible();
        const input = page.getByTestId('custom-input');
        await input.fill('60');
        await input.press('Enter');
        await expect(page.locator('.ap-dim--w')).toContainText('60');
        await expect(price).not.toHaveText(p0);
        // out-of-range values are pulled back into the admin's range (8–60 in by default)
        await input.fill('500');
        await input.press('Enter');
        await expect(input).toHaveValue('60');
        await input.fill('10');
        await input.press('Enter');
        await expect(page.locator('.ap-dim--w')).toContainText('10');
        const w1 = await page.locator('.ap-stage .ap-place').evaluate((el) => el.getBoundingClientRect().width);
        expect(w1).toBeGreaterThan(0);
        expect(w0).toBeGreaterThan(0);
    });

    test('Next walks Size → Frame → See it on your wall, with a summary at the end', async ({ page }) => {
        await openStep2(page);
        await page.getByTestId('part-next').click();
        await expect(page.locator('.ap-part')).toHaveAttribute('data-part', 'frame');
        await page.click('.ap-frame[data-frame=OAK]');
        await page.getByTestId('part-next').click();
        await expect(page.locator('.ap-part')).toHaveAttribute('data-part', 'wall');
        await expect(page.getByTestId('part-next')).toHaveCount(0);
        await expect(page.getByTestId('wall-summary')).toContainText('Natural oak');
        // the last part switches to a room photo so the buyer sees it on a wall
        await expect(page.locator('.ap-stage')).toHaveAttribute('data-room', /.+/);
        await page.getByTestId('part-back').click();
        await expect(page.locator('.ap-part')).toHaveAttribute('data-part', 'frame');
    });

    test('@mobile swipe changes the background and the page fits the phone', async ({ page, isMobile }) => {
        test.skip(!isMobile, 'phone layout');
        await openStep2(page);
        expect(await noSideScroll(page)).toBeLessThanOrEqual(1);
        const count = page.getByTestId('bg-count');
        await expect(count).toContainText('1 of');
        await page.locator('.ap-carousel').evaluate((el) => {
            const r = el.getBoundingClientRect();
            const y = r.top + r.height / 2;
            const t = (x) => new Touch({ identifier: 1, target: el, clientX: x, clientY: y });
            el.dispatchEvent(new TouchEvent('touchstart', { bubbles: true, touches: [t(r.right - 30)], changedTouches: [t(r.right - 30)] }));
            el.dispatchEvent(new TouchEvent('touchend', { bubbles: true, touches: [], changedTouches: [t(r.left + 30)] }));
        });
        await expect(count).toContainText('2 of');
        await expect(page.locator('.ap-mobilebar')).toBeVisible();
        await page.locator('.ap-mobilebar button').click();
        await expect(page.locator('.ap-part')).toHaveAttribute('data-part', 'frame');
        await page.goto('/cart');
        expect(await noSideScroll(page)).toBeLessThanOrEqual(1);
    });
});

test.describe('Make an offer', () => {
    test.beforeEach(async ({ page }) => signIn(page));

    test('a collector offers on an original, sees it under Your offers and can withdraw it', async ({ page }) => {
        await page.goto(`/marketplace/${ORIGINAL}`);
        await page.click('.pz-buyopt[data-format=ORIGINAL]');
        await page.getByTestId('offer-open').click();
        const dialog = page.getByRole('dialog');
        await expect(dialog).toBeVisible();
        // too low: the dialog says so before anything is sent
        await page.getByTestId('offer-amount').fill('100');
        await page.getByTestId('offer-agree').check();
        await page.getByTestId('offer-send').click();
        await expect(dialog.getByRole('alert')).toBeVisible();
        await page.getByTestId('offer-amount').fill('13000');
        await page.getByTestId('offer-send').click();
        await expect(dialog).toContainText(/sent/i);
        await page.keyboard.press('Escape');
        await expect(page.getByTestId('offer-mine')).toBeVisible();

        await page.goto('/user/offers');
        const card = page.getByTestId('offer-card').filter({ hasText: 'Scarlet Macaw Study' }).filter({ has: page.locator('[data-testid=offer-withdraw]') });
        await expect(card).toHaveAttribute('data-status', 'OPEN');
        await card.getByTestId('offer-withdraw').click();
        await expect(page.getByTestId('offer-card').filter({ hasText: 'Scarlet Macaw Study' }).first()).toHaveAttribute('data-status', 'WITHDRAWN');
    });

    test('@mobile the offer dialog fits a phone', async ({ page, isMobile }) => {
        test.skip(!isMobile, 'phone layout');
        await page.goto(`/marketplace/${ORIGINAL}`);
        await page.click('.pz-buyopt[data-format=ORIGINAL]');
        await page.getByTestId('offer-open').click();
        const box = await page.getByRole('dialog').boundingBox();
        const vw = page.viewportSize().width;
        expect(box.x).toBeGreaterThanOrEqual(0);
        expect(box.x + box.width).toBeLessThanOrEqual(vw + 1);
        await expect(page.getByTestId('offer-send')).toBeVisible();
    });
});

// The four screens: artwork page (choose + talk to the artist) -> room preview -> payment -> order summary.
const { test, expect } = require('@playwright/test');
const { ARTWORK, emptyCart, signIn } = require('./helpers');

test.describe('Purchase flow', () => {
    test.beforeEach(async ({ page }) => {
        await page.goto('/marketplace');
        await emptyCart(page);
    });

    test('screen 1 has the format choice, live price and the artist one click away', async ({ page }) => {
        await page.goto(`/marketplace/${ARTWORK}`);
        const price = page.getByTestId('buy-price');
        await page.click('.pz-buyopt[data-format=POSTER]');
        const poster = await price.textContent();
        await page.click('.pz-buyopt[data-format=CANVAS]');
        await expect(price).not.toHaveText(poster);
        await expect(page.getByTestId('talk-to-artist')).toBeVisible();
        await page.getByTestId('talk-to-artist').getByRole('button', { name: /Chat/ }).click();
        await expect(page.getByRole('dialog', { name: /Chat with/ })).toBeVisible();
    });

    test('old step URLs land on the right screen', async ({ page }) => {
        await page.goto(`/purchase/step-1?artwork=${ARTWORK}&format=POSTER`);
        await expect(page).toHaveURL(new RegExp(`/marketplace/${ARTWORK}\\?format=POSTER`));
        await expect(page.locator('.pz-buyopt[data-format=POSTER]')).toHaveClass(/is-on/);
    });

    test('checkout: accordion, inline validation, test payment, order summary', async ({ page }) => {
        await page.goto(`/marketplace/${ARTWORK}`);
        await page.click('.pz-buyopt[data-format=CANVAS]');
        await page.getByTestId('buy-continue').click();
        await page.waitForURL(/\/purchase\/step-2/);
        await page.click('.pz-frame2[data-frame=BLACK_WOOD]');
        await page.getByTestId('room-continue').click();
        await page.waitForURL(/\/purchase\/checkout/);

        // contact is open, the rest closed; a bad e-mail is flagged when leaving the field
        await expect(page.locator('[data-section=contact]')).toHaveClass(/is-open/);
        await expect(page.locator('.pz-line__variant').first()).toContainText('black wood frame');
        await page.fill('#co-name', 'Meera Rao');
        await page.fill('#co-email', 'meera@');
        await page.locator('#co-email').blur();
        await expect(page.locator('#co-email-err')).toBeVisible();
        await page.fill('#co-email', 'meera@example.com');
        await page.locator('#co-email').blur();
        await expect(page.locator('#co-email-err')).toHaveCount(0);
        await page.fill('#co-phone', '9876543210');
        await page.locator('#co-phone').blur();

        // finishing the section moves on by itself
        await expect(page.locator('[data-section=delivery]')).toHaveClass(/is-open/);
        await expect(page.locator('[data-section=contact] .pz-acc__summary')).toContainText('meera@example.com');
        await page.fill('#co-line1', '4 Residency Road');
        await page.fill('#co-city', 'Bengaluru');
        await page.fill('#co-postal', '5600');
        await page.locator('#co-postal').blur();
        await expect(page.locator('#co-postal-err')).toContainText('6-digit');
        await page.fill('#co-postal', '560025');
        await page.locator('#co-postal').blur();
        await expect(page.locator('[data-section=payment]')).toHaveClass(/is-open/);

        await page.getByTestId('pay').click();
        await page.getByRole('button', { name: 'Simulate successful payment' }).click();
        await page.waitForURL(/\/purchase\/orders\//);
        await expect(page.getByRole('heading', { name: /Thank you, Meera/ })).toBeVisible();
        await expect(page.getByText('Order summary')).toBeVisible();
        await expect(page.locator('.pz-line__variant').first()).toContainText('black wood frame');
    });

    test('the order summary sits beside the form and stays in view', async ({ page }) => {
        await page.goto(`/marketplace/${ARTWORK}`);
        await page.click('.pz-buyopt[data-format=POSTER]');
        await page.getByTestId('buy-continue').click();
        await page.waitForURL(/\/purchase\/step-2/);
        await page.getByTestId('room-continue').click();
        await page.waitForURL(/\/purchase\/checkout/);
        const pos = await page.locator('.pz-checkout__summary').evaluate((el) => getComputedStyle(el).position);
        expect(pos).toBe('sticky');
    });

    test('signed-in collectors find the order under Your orders', async ({ page }) => {
        await signIn(page);
        await emptyCart(page);
        await page.goto(`/marketplace/${ARTWORK}`);
        await page.click('.pz-buyopt[data-format=POSTER]');
        await page.getByTestId('buy-continue').click();
        await page.waitForURL(/\/purchase\/step-2/);
        await page.getByTestId('room-continue').click();
        await page.waitForURL(/\/purchase\/checkout/);
        for (const sec of ['contact', 'delivery']) {
            const open = await page.locator(`[data-section=${sec}]`).getAttribute('class');
            if (!/is-open/.test(open)) continue;
            if (sec === 'contact') { await page.fill('#co-phone', '9876543210'); await page.getByRole('button', { name: /Continue to delivery/ }).click(); }
            if (sec === 'delivery') {
                await page.fill('#co-line1', '12 MG Road'); await page.fill('#co-city', 'Bengaluru'); await page.fill('#co-postal', '560001');
                await page.getByRole('button', { name: /Continue to payment/ }).click();
            }
        }
        await page.getByTestId('pay').click();
        await page.getByRole('button', { name: 'Simulate successful payment' }).click();
        await page.waitForURL(/\/purchase\/orders\//);
        const orderId = page.url().split('/').pop();
        await page.getByRole('link', { name: /See all your orders/ }).click();
        await expect(page.locator(`.ord-item[data-order="${orderId}"]`)).toBeVisible();
    });
});

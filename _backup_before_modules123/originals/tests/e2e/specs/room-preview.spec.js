// The "See it in your room" screen: changing the room backdrop must update the visualiser immediately.
const { test, expect } = require('@playwright/test');
const { ARTWORK, emptyCart } = require('./helpers');

const ROOMS = ['living', 'studio', 'bedroom', 'gallery'];

async function openRoomPreview(page, format = 'CANVAS') {
    await page.goto(`/marketplace/${ARTWORK}`);
    await page.click(`.pz-buyopt[data-format=${format}]`);
    await page.getByTestId('buy-continue').click();
    await page.waitForURL(/\/purchase\/step-2/);
    await expect(page.locator('.pz-stage--photo')).toBeVisible();
}

test.describe('Room preview', () => {
    test.beforeEach(async ({ page }) => {
        await page.goto('/marketplace');
        await emptyCart(page);
        await page.evaluate(() => { try { window.sessionStorage.clear(); } catch (e) { /* ignore */ } });
    });

    test('switching the room backdrop updates the visualiser immediately', async ({ page }) => {
        await openRoomPreview(page);
        const stage = page.locator('.pz-stage--photo');
        await expect(stage).toHaveAttribute('data-room', 'living');
        const art = page.locator('.pz-stage--photo .pz-art');
        const before = await art.boundingBox();

        for (const id of ROOMS.slice(1).concat('living')) {
            // click and read the stage in the same task: the new room must already be on screen, no reload or delay
            const now = await page.evaluate((room) => {
                document.querySelector(`.pz-roomthumb[data-room="${room}"]`).click();
                const st = document.querySelector('.pz-stage--photo');
                return {
                    room: st.getAttribute('data-room'),
                    photo: st.querySelector('.pz-stage__photo').getAttribute('src'),
                    front: st.querySelector('.pz-stage__front').getAttribute('src'),
                    shade: st.querySelector('.pz-art__shade').style.backgroundImage,
                    pressed: document.querySelector(`.pz-roomthumb[data-room="${room}"]`).getAttribute('aria-checked'),
                };
            }, id);
            expect(now.room).toBe(id);
            expect(now.photo).toBe(`/assets/rooms/${id}.jpg`);
            expect(now.front).toBe(`/assets/rooms/${id}-front.webp`);
            expect(now.shade).toContain(`/assets/rooms/${id}-shade.jpg`);
            expect(now.pressed).toBe('true');
            // the photo really loads, and the artwork is still hanging on the wall at the same scale
            await expect.poll(() => page.locator('.pz-stage__photo').evaluate((img) => img.complete && img.naturalWidth > 0)).toBe(true);
            await expect(art).toBeVisible();
            const box = await art.boundingBox();
            expect(Math.abs(box.width - before.width)).toBeLessThan(2);
        }
    });

    test('the chosen room is remembered and size and frame change the artwork on the wall', async ({ page }) => {
        await openRoomPreview(page);
        await page.click('.pz-roomthumb[data-room="studio"]');
        await page.reload();
        await expect(page.locator('.pz-stage--photo')).toHaveAttribute('data-room', 'studio');

        const art = page.locator('.pz-stage--photo .pz-art');
        const small = await art.boundingBox();
        await page.getByRole('button', { name: /36 × 36/ }).click();
        await expect.poll(async () => (await art.boundingBox()).width).toBeGreaterThan(small.width * 1.3);
        await expect(art).toHaveAttribute('data-width-in', '36');

        await page.click('.pz-frame2[data-frame=OAK]');
        await expect(art).toHaveClass(/pz-art--oak/);
        await expect(page.getByTestId('room-price')).not.toHaveText('');
    });

    test('the artwork can be dragged and moved with the keyboard, but stays on the wall', async ({ page }) => {
        await openRoomPreview(page);
        const art = page.locator('.pz-stage--photo .pz-art');
        const a = await art.boundingBox();
        await page.mouse.move(a.x + a.width / 2, a.y + a.height / 2);
        await page.mouse.down();
        await page.mouse.move(a.x + a.width / 2 - 120, a.y + a.height / 2 - 40, { steps: 6 });
        await page.mouse.up();
        const b = await art.boundingBox();
        expect(b.x).toBeLessThan(a.x - 60);
        await art.focus();
        for (let i = 0; i < 200; i++) await page.keyboard.press('ArrowLeft');
        const stage = await page.locator('.pz-stage--photo').boundingBox();
        const c = await art.boundingBox();
        expect(c.x).toBeGreaterThanOrEqual(stage.x);
    });

    test('mobile: room preview fits the screen @mobile', async ({ page }) => {
        await openRoomPreview(page);
        await page.click('.pz-roomthumb[data-room="bedroom"]');
        await expect(page.locator('.pz-stage--photo')).toHaveAttribute('data-room', 'bedroom');
        // a wider layout makes mobile browsers zoom out (innerWidth grows), so compare with the device width
        const overflow = await page.evaluate(() => Math.max(document.documentElement.scrollWidth, window.innerWidth) - document.documentElement.clientWidth);
        expect(overflow).toBeLessThanOrEqual(1);
    });
});

// Two-step verification for staff: set it up in Account › Security (QR code, key, recovery codes), then sign in with a
// code from the authenticator app. Runs against the dev profile; turns MFA off again at the end.
const { test, expect } = require('@playwright/test');
const crypto = require('crypto');

function totp(secret, offset = 0) {
    const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
    let bits = '';
    for (const ch of secret.replace(/\s/g, '')) bits += alphabet.indexOf(ch).toString(2).padStart(5, '0');
    const key = Buffer.from(bits.match(/.{8}/g).map((b) => parseInt(b, 2)));
    const step = Math.floor(Date.now() / 30000) + offset;
    const msg = Buffer.alloc(8);
    msg.writeBigUInt64BE(BigInt(step));
    const h = crypto.createHmac('sha1', key).update(msg).digest();
    const o = h[h.length - 1] & 15;
    return String((h.readUInt32BE(o) & 0x7fffffff) % 1000000).padStart(6, '0');
}
async function nextStep() { await new Promise((r) => setTimeout(r, 30000 - (Date.now() % 30000) + 300)); }
async function password(page) {
    await page.goto('/admin/login');
    await page.fill('input[name=email]', 'admin@artistikcity.com');
    await page.fill('input[name=password]', 'password');
    await page.locator('button[type=submit]').click();
}

test.describe.serial('Admin two-step verification', () => {
    test.setTimeout(150000);
    let secret;
    test('set up in Account › Security, then sign in with a code', async ({ page, isMobile }) => {
        test.skip(isMobile, 'desktop only');
        await password(page);
        await page.waitForURL(/\/admin\/dashboard/);
        await page.locator('.adm-account__btn').click();
        await page.getByRole('menuitem', { name: 'Two-step verification' }).click();
        await expect(page.getByTestId('account-security')).toBeVisible();
        await page.getByTestId('mfa-start').click();
        await expect(page.locator('[data-testid=mfa-qr] svg')).toBeVisible();
        await page.locator('[data-testid=mfa-setup] summary').click();
        secret = (await page.getByTestId('mfa-secret').textContent()).replace(/\s/g, '');
        await page.getByTestId('mfa-code').fill(totp(secret));
        await page.getByTestId('mfa-enable').click();
        await expect(page.locator('[data-testid=mfa-recovery-codes] li')).toHaveCount(10);
        await page.getByTestId('mfa-codes-done').click();
        await expect(page.getByTestId('mfa-on')).toBeVisible();
        // sign out and in again: now the code step
        await page.context().clearCookies();
        await password(page);
        await expect(page).toHaveURL(/\/admin\/login\/verify$/);
        await nextStep();
        await page.getByTestId('mfa-code').fill(totp(secret));
        await page.getByTestId('mfa-submit').click();
        await page.waitForURL(/\/admin\/dashboard/);
        // tidy up: turn it off
        await page.goto('/admin/account/security');
        await page.getByTestId('mfa-disable').click();
        await nextStep();
        await page.getByTestId('mfa-code').fill(totp(secret));
        await page.getByTestId('mfa-confirm').click();
        await expect(page.getByTestId('mfa-start')).toBeVisible();
    });
});
